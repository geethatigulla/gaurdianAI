package ai.guardian.platform.overlay

import ai.guardian.data.repo.AnalyzeScanUseCase
import ai.guardian.data.repo.ScanRecord
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import javax.inject.Inject

/** Collects the visible text of the app(s) on screen. Password fields and Guardian itself are skipped. */
object ScreenReader {
    private const val MAX_NODES = 1500
    private const val MAX_CHARS = 5_000

    fun visibleText(service: AccessibilityService): String {
        val roots = runCatching {
            service.windows
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root }
        }.getOrDefault(emptyList()).ifEmpty { listOfNotNull(service.rootInActiveWindow) }

        val seen = LinkedHashSet<String>()
        var visited = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        roots.filter { it.packageName?.toString() != service.packageName }.forEach(queue::addLast)

        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (!node.isVisibleToUser || node.isPassword) continue
            listOfNotNull(node.text, node.contentDescription)
                .map { it.toString().trim() }
                .filter { it.length >= 2 }
                .forEach { seen += it }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return seen.joinToString("\n").take(MAX_CHARS)
    }
}

data class ScreenScanOutcome(
    /** The most serious finding on screen, or null if nothing could be read. */
    val record: ScanRecord?,
    val foundQr: Boolean,
)

/** Checks everything found on screen (text + any QR code) and keeps the most serious result. */
class ScreenScanUseCase @Inject constructor(private val analyze: AnalyzeScanUseCase) {
    suspend fun scan(screenText: String, qrPayload: String?): ScreenScanOutcome {
        val results = buildList {
            if (qrPayload != null) add(analyze(EventType.QR_EVENT, qrPayload, EventSource.SCREEN))
            if (screenText.isNotBlank()) add(analyze(EventType.MANUAL_TEXT_EVENT, screenText, EventSource.SCREEN))
        }
        val worst = results.maxWithOrNull(compareBy<ScanRecord>({ it.result.riskScore }, { it.result.indicators.size }))
        return ScreenScanOutcome(worst, qrPayload != null)
    }
}
