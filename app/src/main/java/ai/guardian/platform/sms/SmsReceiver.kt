package ai.guardian.platform.sms

import ai.guardian.data.repo.AnalyzeScanUseCase
import ai.guardian.data.repo.SettingsRepository
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.platform.notify.ThreatNotifier
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Checks incoming SMS alongside the default messaging app. Android delivers SMS_RECEIVED to us at
 * the same time as the user's SMS app — we cannot hold, hide or block a message, only warn.
 * Does nothing unless the user turned on "Automatic SMS checks".
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var analyze: AnalyzeScanUseCase
    @Inject lateinit var notifier: ThreatNotifier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return

        // Long messages arrive as several parts from the same sender; join them back together.
        val messages = parts.groupBy { it.originatingAddress }
            .map { (sender, list) -> sender to list.joinToString("") { it.messageBody.orEmpty() } }
            .filter { it.second.isNotBlank() }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(TIMEOUT_MS) {
                    val prefs = settings.current()
                    if (!prefs.smsMonitoring) return@withTimeoutOrNull
                    for ((sender, body) in messages) {
                        val record = analyze(EventType.SMS_EVENT, body, EventSource.SMS_RECEIVER, sender)
                        if (prefs.notificationsEnabled && record.result.riskScore >= prefs.alertThreshold) {
                            notifier.notifySuspicious(record, "SMS", sender, prefs.lockScreenPrivacy)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L // Broadcast receivers must finish within ~10 s.
    }
}
