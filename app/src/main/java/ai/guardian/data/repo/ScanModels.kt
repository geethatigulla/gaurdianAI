package ai.guardian.data.repo

import ai.guardian.engine.extract.DomainUtils
import ai.guardian.engine.extract.UrlExtractor
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.RiskLevel
import ai.guardian.engine.model.RiskResult
import ai.guardian.engine.model.SecurityEvent

/** A scan as the UI sees it: the engine result plus app-side metadata. */
data class ScanRecord(
    val result: RiskResult,
    val source: EventSource,
    val displayLabel: String,
    val rawInputRedacted: String?,
    val userFeedback: UserFeedback?,
    /** False when history is off (retention = 0) and the result lives only in memory. */
    val savedToHistory: Boolean,
    /** Web address the user can choose to open after checking. Kept in memory only, never stored. */
    val openableUrl: String? = null,
    /** Public sites linked from this scan that the user could choose to trust. Memory only. */
    val linkDomains: List<String> = emptyList(),
)

data class ScanSummary(
    val id: String,
    val eventType: EventType,
    val riskScore: Int,
    val riskLevel: RiskLevel,
    val category: String,
    val displayLabel: String,
    val createdAt: Long,
)

data class DashboardStats(
    val scansLast30Days: Int,
    val warningsLast30Days: Int,
    val recent: List<ScanSummary>,
)

enum class UserFeedback(val label: String) {
    CORRECT("Looks right"),
    FALSE_ALARM("False alarm"),
    MISSED_SCAM("It was a scam"),
}

object OpenableUrl {
    /** Only plain web links are ever offered for opening; never upi:, intent:, javascript: etc. */
    fun forEvent(event: SecurityEvent): String? {
        val input = event.rawInput.trim()
        val candidate = when (event.type) {
            EventType.URL_EVENT -> UrlExtractor.parseSingle(input)?.let { if (it.scheme == null) "https://${it.original}" else it.original }
            EventType.QR_EVENT -> input.takeIf { !it.any(Char::isWhitespace) }
            else -> null
        } ?: return null
        return candidate.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
    }
}

object LinkDomains {
    /** Registrable domains of the public links in an event (local addresses excluded). */
    fun forEvent(event: SecurityEvent): List<String> {
        val urls = when (event.type) {
            EventType.URL_EVENT -> listOfNotNull(UrlExtractor.parseSingle(event.rawInput))
            EventType.CALL_EVENT, EventType.IP_EVENT, EventType.DOMAIN_EVENT -> emptyList()
            else -> UrlExtractor.extract(event.rawInput)
        }
        return urls.filter { !it.isIpHost && !DomainUtils.isLocalOrPrivate(it.host) }.map { it.registrableDomain }.distinct().take(5)
    }
}

object DisplayLabel {
    /** Never includes message text — only the kind of input and, at most, a link's host. */
    fun forEvent(event: SecurityEvent): String {
        val input = event.rawInput.trim()
        return when (event.type) {
            EventType.URL_EVENT -> "Link · " + (UrlExtractor.parseSingle(input)?.displayHost ?: input.take(40))
            EventType.QR_EVENT -> "QR code · " + (UrlExtractor.extract(input).firstOrNull()?.displayHost ?: "${input.length} chars")
            EventType.SMS_EVENT -> "SMS" + (event.sender?.let { " from $it" } ?: "") + messageSuffix(input)
            EventType.APP_MESSAGE_EVENT -> (event.channel ?: "App") + (event.sender?.let { " from $it" } ?: "") + messageSuffix(input)
            EventType.MANUAL_TEXT_EVENT -> "Message" + messageSuffix(input)
            EventType.CALL_EVENT -> "Number · $input"
            EventType.IP_EVENT -> "IP · $input"
            EventType.DOMAIN_EVENT -> "Domain · $input"
        }
    }

    private fun messageSuffix(text: String): String {
        val host = UrlExtractor.extract(text).firstOrNull()?.displayHost
        return if (host != null) " · link to $host" else " · ${text.length} chars"
    }
}
