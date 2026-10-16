package ai.guardian.engine.model

/** The single input to the engine. The app builds one of these for every scan. */
data class SecurityEvent(
    val id: String,
    val type: EventType,
    val rawInput: String,
    val source: EventSource,
    val timestamp: Long,
    /** Optional sender address/number (SMS). Never used as proof of identity. */
    val sender: String? = null,
    /** Human name of the app the message came from (e.g. "WhatsApp"), for APP_MESSAGE_EVENT. */
    val channel: String? = null,
)

/**
 * One observed warning sign.
 *
 * [family] groups correlated signals: the risk engine only counts the strongest indicator per
 * family, so "urgent" + "immediately" + "today" is one URGENCY hit, not three.
 */
data class Indicator(
    val type: String,
    val family: String,
    val severity: Severity,
    val description: String,
    val source: String,
    val matchedText: String? = null,
)

/** A check the engine attempted (or honestly could not attempt). */
data class Check(val name: String, val status: CheckStatus, val note: String? = null)

/** The only contract between `engine` and the app. */
data class RiskResult(
    val eventId: String,
    val eventType: EventType,
    val riskScore: Int,
    val riskLevel: RiskLevel,
    val category: String,
    val evidenceQuality: EvidenceQuality,
    val summary: String,
    val indicators: List<Indicator>,
    val checks: List<Check>,
    val recommendation: String,
    val scoringVersion: String,
    val analyzedAt: Long,
)
