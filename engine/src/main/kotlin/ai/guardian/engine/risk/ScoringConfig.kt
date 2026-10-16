package ai.guardian.engine.risk

import ai.guardian.engine.model.Severity

/**
 * All scoring knobs in one place. The [version] is stored with every result; any change to a
 * weight or cap MUST bump it so old history stays interpretable. Not calibrated — see
 * docs/risk-scoring.md.
 */
data class ScoringConfig(
    val version: String = "0.1-local",
    val severityWeights: Map<Severity, Double> = mapOf(
        Severity.WEAK to 0.15,
        Severity.MEDIUM to 0.35,
        Severity.STRONG to 0.60,
    ),
    /** When every indicator is WEAK the score can't exceed this (never above GUARDED). */
    val weakOnlyCap: Int = 39,
) {
    fun weight(severity: Severity): Double = severityWeights.getValue(severity)

    companion object {
        val DEFAULT = ScoringConfig()
    }
}
