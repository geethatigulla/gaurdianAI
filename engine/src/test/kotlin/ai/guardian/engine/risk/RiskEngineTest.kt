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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RiskEngineTest {
    private val engine = RiskEngine()

    private fun ind(family: String, severity: Severity, type: String = "T_$family") =
        Indicator(type = type, family = family, severity = severity, description = "d", source = "test")

    private fun score(vararg indicators: Indicator) = engine.assess(EventType.MANUAL_TEXT_EVENT, indicators.toList(), emptyList()).score

    @Test
    fun `no indicators scores zero, LOW, no known indicators`() {
        val result = engine.assess(EventType.MANUAL_TEXT_EVENT, emptyList(), emptyList())
        assertEquals(0, result.score)
        assertEquals(RiskLevel.LOW, result.level)
        assertEquals(Category.NO_KNOWN_INDICATORS, result.category)
    }

    @Test
    fun `one weak signal alone is 15 and LOW`() {
        assertEquals(15, score(ind(Family.URGENCY, Severity.WEAK)))
    }

    @Test
    fun `one strong signal alone is 60 and HIGH`() {
        val result = engine.assess(EventType.MANUAL_TEXT_EVENT, listOf(ind(Family.CREDENTIAL_REQUEST, Severity.STRONG)), emptyList())
        assertEquals(60, result.score)
        assertEquals(RiskLevel.HIGH, result.level)
    }

    @Test
    fun `strong plus medium combine with noisy-OR to 74`() {
        assertEquals(74, score(ind(Family.CREDENTIAL_REQUEST, Severity.STRONG), ind(Family.ACCOUNT_THREAT, Severity.MEDIUM)))
    }

    @Test
    fun `strong plus two mediums reaches CRITICAL 83`() {
        val s = score(
            ind(Family.CREDENTIAL_REQUEST, Severity.STRONG),
            ind(Family.ACCOUNT_THREAT, Severity.MEDIUM),
            ind(Family.IMPERSONATION, Severity.MEDIUM),
        )
        assertEquals(83, s)
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromScore(s))
    }

    @Test
    fun `signals in the same family are not summed`() {
        val single = score(ind(Family.URGENCY, Severity.WEAK, "A"))
        val triple = score(ind(Family.URGENCY, Severity.WEAK, "A"), ind(Family.URGENCY, Severity.WEAK, "B"), ind(Family.URGENCY, Severity.WEAK, "C"))
        assertEquals(single, triple)
    }

    @Test
    fun `family takes its strongest member`() {
        assertEquals(60, score(ind(Family.URL_IMPERSONATION, Severity.MEDIUM, "A"), ind(Family.URL_IMPERSONATION, Severity.STRONG, "B")))
    }

    @Test
    fun `weak-only signals are capped at 39 no matter how many`() {
        val s = score(
            ind(Family.URGENCY, Severity.WEAK),
            ind(Family.URL_STRUCTURE, Severity.WEAK),
            ind(Family.URL_TRANSPORT, Severity.WEAK),
            ind(Family.URL_OBFUSCATION, Severity.WEAK),
            ind(Family.IMPERSONATION, Severity.WEAK),
        )
        assertEquals(39, s)
        assertEquals(RiskLevel.GUARDED, RiskLevel.fromScore(s))
    }

    @Test
    fun `score never exceeds 100`() {
        val all = listOf(Family.URGENCY, Family.ACCOUNT_THREAT, Family.CREDENTIAL_REQUEST, Family.IMPERSONATION, Family.APK_LURE, Family.PAYMENT_LURE, Family.URL_IMPERSONATION)
            .map { ind(it, Severity.STRONG) }
        val s = engine.assess(EventType.MANUAL_TEXT_EVENT, all, emptyList()).score
        assert(s in 0..100)
    }

    @ParameterizedTest
    @CsvSource("0,LOW", "19,LOW", "20,GUARDED", "39,GUARDED", "40,MODERATE", "59,MODERATE", "60,HIGH", "79,HIGH", "80,CRITICAL", "100,CRITICAL")
    fun `level bands match the documented boundaries`(score: Int, level: RiskLevel) {
        assertEquals(level, RiskLevel.fromScore(score))
    }

    @Test
    fun `evidence quality is HIGH when all checks completed`() {
        val checks = listOf(Check("a", CheckStatus.COMPLETED), Check("b", CheckStatus.COMPLETED))
        assertEquals(EvidenceQuality.HIGH, engine.evidenceQuality(checks))
    }

    @Test
    fun `evidence quality is MODERATE when half the checks are unavailable`() {
        val checks = listOf(Check("rules", CheckStatus.COMPLETED), Check("reputation", CheckStatus.UNAVAILABLE))
        assertEquals(EvidenceQuality.MODERATE, engine.evidenceQuality(checks))
    }

    @Test
    fun `evidence quality is LOW when most checks failed or are unavailable`() {
        val checks = listOf(Check("a", CheckStatus.COMPLETED), Check("b", CheckStatus.FAILED), Check("c", CheckStatus.UNAVAILABLE))
        assertEquals(EvidenceQuality.LOW, engine.evidenceQuality(checks))
        assertEquals(EvidenceQuality.LOW, engine.evidenceQuality(emptyList()))
    }

    @Test
    fun `skipped checks do not count against evidence quality`() {
        val checks = listOf(Check("a", CheckStatus.COMPLETED), Check("b", CheckStatus.SKIPPED))
        assertEquals(EvidenceQuality.HIGH, engine.evidenceQuality(checks))
    }

    @Test
    fun `evidence quality is independent of the score`() {
        val checks = listOf(Check("rules", CheckStatus.COMPLETED))
        val result = engine.assess(EventType.MANUAL_TEXT_EVENT, emptyList(), checks)
        assertEquals(0, result.score)
        assertEquals(EvidenceQuality.HIGH, result.evidenceQuality)
    }

    @Test
    fun `category prefers the more serious family`() {
        val fam = mapOf(Family.URGENCY to Severity.WEAK, Family.PAYMENT_LURE to Severity.STRONG, Family.REWARD_LURE to Severity.MEDIUM)
        assertEquals(Category.PAYMENT_FRAUD, engine.categorize(EventType.MANUAL_TEXT_EVENT, fam))
    }

    @Test
    fun `weak hint does not define category when a medium family exists`() {
        val fam = mapOf(Family.URL_OBFUSCATION to Severity.WEAK, Family.JOB_LURE to Severity.MEDIUM)
        assertEquals(Category.JOB_SCAM, engine.categorize(EventType.MANUAL_TEXT_EVENT, fam))
    }

    @Test
    fun `link-only findings on a QR event are categorised as suspicious QR`() {
        val fam = mapOf(Family.URL_OBFUSCATION to Severity.MEDIUM)
        assertEquals(Category.SUSPICIOUS_QR, engine.categorize(EventType.QR_EVENT, fam))
        assertEquals(Category.SUSPICIOUS_LINK, engine.categorize(EventType.URL_EVENT, fam))
    }

    @Test
    fun `changing weights requires and reports a new version`() {
        val custom = RiskEngine(ScoringConfig(version = "0.1-test", severityWeights = mapOf(Severity.WEAK to 0.1, Severity.MEDIUM to 0.3, Severity.STRONG to 0.5)))
        assertEquals("0.1-test", custom.scoringVersion)
        assertEquals(50, custom.score(mapOf(Family.APK_LURE to Severity.STRONG)))
        assertEquals("0.1-local", RiskEngine().scoringVersion)
    }
}
