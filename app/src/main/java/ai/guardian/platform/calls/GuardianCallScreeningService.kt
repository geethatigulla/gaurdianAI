package ai.guardian.platform.calls

import ai.guardian.data.repo.ScanRecord
import ai.guardian.data.repo.ScanRepository
import ai.guardian.data.repo.SettingsRepository
import ai.guardian.di.AppClock
import ai.guardian.engine.Analyzer
import ai.guardian.engine.detect.caller.PhoneNumbers
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.SecurityEvent
import ai.guardian.platform.notify.ThreatNotifier
import android.telecom.Call
import android.telecom.CallScreeningService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Checks an incoming caller's number. Only risky calls are saved, so normal calls leave no trace. */
@Singleton
class CallChecker @Inject constructor(
    private val analyzer: Analyzer,
    private val repository: ScanRepository,
    private val clock: AppClock,
) {
    suspend fun check(number: String, reportedNumbers: Set<String>, alertThreshold: Int): ScanRecord? {
        val event = SecurityEvent(UUID.randomUUID().toString(), EventType.CALL_EVENT, number, EventSource.CALL_SCREENING, clock.now())
        val result = analyzer.analyze(event, reportedNumbers = reportedNumbers)
        if (result.riskScore < alertThreshold) return null
        return repository.save(event, result)
    }
}

/**
 * Android calls this for incoming calls from numbers not in the user's contacts, before the phone
 * rings, once Guardian holds the call-screening role. Guardian never sees or hears the call itself.
 * It only blocks numbers the user reported, and only if they turned blocking on.
 */
@AndroidEntryPoint
class GuardianCallScreeningService : CallScreeningService() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var checker: CallChecker
    @Inject lateinit var notifier: ThreatNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onScreenCall(callDetails: Call.Details) {
        val incoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        val number = callDetails.handle?.schemeSpecificPart
        if (!incoming || number.isNullOrBlank()) {
            respondToCall(callDetails, allow())
            return
        }
        scope.launch {
            val prefs = settings.current()
            val reported = prefs.reportedNumbers.mapNotNull(PhoneNumbers::normalize).toSet()
            val isReported = PhoneNumbers.normalize(number) in reported
            val block = prefs.callScreening && prefs.blockReportedCalls && isReported
            // Must answer Android within 5 seconds, so respond before the (fast) analysis.
            respondToCall(callDetails, if (block) reject() else allow())

            if (!prefs.callScreening) return@launch
            val record = runCatching { checker.check(number, reported, prefs.alertThreshold) }.getOrNull() ?: return@launch
            if (prefs.notificationsEnabled) {
                notifier.notifySuspicious(record, if (block) "call (blocked)" else "call", number, prefs.lockScreenPrivacy)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun allow() = CallResponse.Builder().build()

    private fun reject() = CallResponse.Builder()
        .setDisallowCall(true)
        .setRejectCall(true)
        .setSkipNotification(false)
        .build()
}
