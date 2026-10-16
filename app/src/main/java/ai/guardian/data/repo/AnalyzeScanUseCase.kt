package ai.guardian.data.repo

import ai.guardian.di.AppClock
import ai.guardian.engine.Analyzer
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.SecurityEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/** Builds a [SecurityEvent], runs the engine off the main thread and saves the result. */
class AnalyzeScanUseCase @Inject constructor(
    private val analyzer: Analyzer,
    private val repository: ScanRepository,
    private val clock: AppClock,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(
        type: EventType,
        input: String,
        source: EventSource,
        sender: String? = null,
        channel: String? = null,
    ): ScanRecord {
        val event = SecurityEvent(
            id = UUID.randomUUID().toString(),
            type = type,
            rawInput = input,
            source = source,
            timestamp = clock.now(),
            sender = sender,
            channel = channel,
        )
        val prefs = settings.current()
        val result = withContext(Dispatchers.Default) { analyzer.analyze(event, prefs.trustedDomains, prefs.reportedNumbers) }
        return repository.save(event, result)
    }
}
