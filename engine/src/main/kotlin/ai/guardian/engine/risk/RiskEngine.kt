package ai.guardian.engine.risk

import ai.guardian.engine.model.Category
import ai.guardian.engine.model.Check
import ai.guardian.engine.model.CheckStatus
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.EvidenceQuality
import ai.guardian.engine.model.Family
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.RiskLevel
import ai.guardian.engine.model.Severity
import kotlin.math.roundToInt

data class RiskAssessment(
    val score: Int,
    val level: RiskLevel,
    val category: String,
    val evidenceQuality: EvidenceQuality,
    /** Strongest severity per family — what actually drove the score. */
    val familySeverities: Map<String, Severity>,
)

/**
 * v0.1-local scoring:
 *  1. Per family keep only the strongest indicator (correlated signals don't stack).
 *  2. Map severity → weight.
 *  3. Combine families with noisy-OR: p = 1 − Π(1 − w). Score = round(p × 100).
 *  4. If every indicator is WEAK, cap at [ScoringConfig.weakOnlyCap].
 *  5. Evidence quality is computed from checks, independently of the score.
 */
class RiskEngine(private val config: ScoringConfig = ScoringConfig.DEFAULT) {

    val scoringVersion: String get() = config.version

    fun assess(eventType: EventType, indicators: List<Indicator>, checks: List<Check>): RiskAssessment {
        val familySeverities = strongestPerFamily(indicators)
        val score = score(familySeverities)
        return RiskAssessment(
            score = score,
            level = RiskLevel.fromScore(score),
            category = categorize(eventType, familySeverities),
            evidenceQuality = evidenceQuality(checks),
            familySeverities = familySeverities,
        )
    }

    fun strongestPerFamily(indicators: List<Indicator>): Map<String, Severity> =
        indicators.groupBy { it.family }.mapValues { (_, list) -> list.maxOf { it.severity } }

    fun score(familySeverities: Map<String, Severity>): Int {
        if (familySeverities.isEmpty()) return 0
        val notTriggered = familySeverities.values.fold(1.0) { acc, severity -> acc * (1.0 - config.weight(severity)) }
        val raw = ((1.0 - notTriggered) * 100).roundToInt().coerceIn(0, 100)
        val weakOnly = familySeverities.values.all { it == Severity.WEAK }
        return if (weakOnly) minOf(raw, config.weakOnlyCap) else raw
    }

    /**
     * SKIPPED checks are "not applicable" and excluded. HIGH = everything applicable completed;
     * MODERATE = at least half; LOW otherwise.
     */
    fun evidenceQuality(checks: List<Check>): EvidenceQuality {
        val applicable = checks.filter { it.status != CheckStatus.SKIPPED }
        if (applicable.isEmpty()) return EvidenceQuality.LOW
        val completed = applicable.count { it.status == CheckStatus.COMPLETED }
        return when {
            completed == applicable.size -> EvidenceQuality.HIGH
            completed * 2 >= applicable.size -> EvidenceQuality.MODERATE
            else -> EvidenceQuality.LOW
        }
    }

    /**
     * Picks the category from the most serious family present. Families at MEDIUM or above are
     * considered first so a single weak hint doesn't define the category.
     */
    fun categorize(eventType: EventType, familySeverities: Map<String, Severity>): String {
        if (familySeverities.isEmpty()) return Category.NO_KNOWN_INDICATORS
        val significant = familySeverities.filterValues { it >= Severity.MEDIUM }.keys.ifEmpty { familySeverities.keys }

        val linkCategory = if (eventType == EventType.QR_EVENT) Category.SUSPICIOUS_QR else Category.SUSPICIOUS_LINK
        val priority: List<Pair<String, String>> = listOf(
            Family.REMOTE_ACCESS_LURE to Category.REMOTE_ACCESS_SCAM,
            Family.APK_LURE to Category.MALWARE_LURE,
            Family.AUTHORITY_THREAT to Category.AUTHORITY_SCAM,
            Family.PAYMENT_LURE to Category.PAYMENT_FRAUD,
            Family.CREDENTIAL_REQUEST to Category.PHISHING,
            Family.URL_IMPERSONATION to Category.PHISHING,
            Family.IMPERSONATION to Category.PHISHING,
            Family.ACCOUNT_THREAT to Category.PHISHING,
            Family.JOB_LURE to Category.JOB_SCAM,
            Family.REWARD_LURE to Category.PRIZE_SCAM,
            Family.QR_PAYLOAD to Category.SUSPICIOUS_QR,
            Family.URL_OBFUSCATION to linkCategory,
            Family.URL_STRUCTURE to linkCategory,
            Family.URL_TRANSPORT to linkCategory,
            Family.NUMBER_FORMAT to Category.SUSPICIOUS_NUMBER,
            Family.NETWORK_ADDRESS to Category.SUSPICIOUS_ADDRESS,
        )
        return priority.firstOrNull { (family, _) -> family in significant }?.second ?: Category.SUSPICIOUS_MESSAGE
    }
}
