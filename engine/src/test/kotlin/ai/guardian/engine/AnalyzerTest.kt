package ai.guardian.engine

import ai.guardian.engine.detect.DetectionContext
import ai.guardian.engine.detect.Detector
import ai.guardian.engine.detect.DetectorOutput
import ai.guardian.engine.model.Category
import ai.guardian.engine.model.CheckStatus
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.EvidenceQuality
import ai.guardian.engine.model.RiskLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end fixtures. Every message here is synthetic and written for this project — modelled on
 * publicly described scam patterns, not scraped from real victims.
 */
class AnalyzerTest {

    private fun text(body: String, sender: String? = null) =
        analyzer.analyze(TestEvents.text(body, sender, if (sender != null) EventType.SMS_EVENT else EventType.MANUAL_TEXT_EVENT))

    // ---- Scams -------------------------------------------------------------------------------

    @Test
    fun `KYC phishing SMS with look-alike link is CRITICAL phishing`() {
        val r = text("Dear customer, your SBI account will be blocked today. Update your KYC immediately: http://sbi-kyc-update.xyz/login")
        assertEquals(RiskLevel.CRITICAL, r.riskLevel)
        assertEquals(Category.PHISHING, r.category)
        assertTrue(r.indicators.types().containsAll(setOf("BRAND_LINK_MISMATCH", "BRAND_IN_UNRELATED_DOMAIN", "KYC_PRESSURE")))
    }

    @Test
    fun `OTP request alone is HIGH`() {
        val r = text("Hi this is Rahul from HDFC bank. Kindly share the OTP you just received to stop the unauthorised transaction.")
        assertTrue(r.riskScore >= 60, "score ${r.riskScore}")
        assertEquals(Category.PHISHING, r.category)
    }

    @Test
    fun `digital arrest threat is an authority scam`() {
        val r = text("This is CBI officer. A parcel with illegal drugs is registered in your name. You are under digital arrest. Pay Rs 50000 to clear your name.")
        assertTrue(r.riskLevel >= RiskLevel.HIGH, "level ${r.riskLevel}")
        assertEquals(Category.AUTHORITY_SCAM, r.category)
    }

    @Test
    fun `UPI PIN to receive cashback is payment fraud`() {
        val r = text("Congratulations! You have won Rs 5000 cashback. Enter your UPI PIN to receive the money in your account.")
        assertTrue(r.riskLevel >= RiskLevel.HIGH)
        assertEquals(Category.PAYMENT_FRAUD, r.category)
    }

    @Test
    fun `electricity APK scam is a CRITICAL malware lure`() {
        val r = text("Your electricity connection will be disconnected tonight at 9:30 pm. Download the app from this link to pay bill: http://45.67.89.10/EB-bill.apk")
        assertEquals(RiskLevel.CRITICAL, r.riskLevel)
        assertEquals(Category.MALWARE_LURE, r.category)
    }

    @Test
    fun `task job scam is at least GUARDED`() {
        val r = text("Part time job! Earn Rs 3000 daily by liking YouTube videos. Contact on WhatsApp wa.me/919812345678")
        assertTrue(r.riskLevel >= RiskLevel.GUARDED)
        assertEquals(Category.JOB_SCAM, r.category)
    }

    @Test
    fun `remote access request is HIGH`() {
        val r = text("To get your refund, please install AnyDesk and tell me the 9-digit code.")
        assertTrue(r.riskLevel >= RiskLevel.HIGH)
        assertEquals(Category.REMOTE_ACCESS_SCAM, r.category)
    }

    @Test
    fun `bank SMS from a personal number with a link is flagged`() {
        val r = text("SBI: Your YONO account is suspended. Verify your PAN at https://yono-sbi-verify.web.app", sender = "+919876543210")
        assertTrue(r.riskLevel >= RiskLevel.HIGH)
        assertTrue("PERSONAL_NUMBER_SENDER" in r.indicators.types())
    }

    // ---- Legitimate --------------------------------------------------------------------------

    @Test
    fun `genuine OTP message is LOW`() {
        val r = text("482913 is your OTP for login to HDFC Bank NetBanking. OTP valid for 5 minutes. Do not share this OTP with anyone. -HDFC Bank")
        assertEquals(RiskLevel.LOW, r.riskLevel, "indicators ${r.indicators}")
    }

    @Test
    fun `genuine debit alert is LOW`() {
        val r = text("Rs.500.00 debited from A/c XX1234 on 12-09-26 to VPA merchant@okaxis. Not you? Call 18001234567. -SBI")
        assertEquals(RiskLevel.LOW, r.riskLevel, "indicators ${r.indicators}")
    }

    @Test
    fun `personal chat has no indicators and an honest summary`() {
        val r = text("Hey, are we still meeting for lunch tomorrow at 1? Let me know.")
        assertEquals(0, r.riskScore)
        assertEquals(Category.NO_KNOWN_INDICATORS, r.category)
        assertTrue(r.summary.contains("not proof"))
    }

    @Test
    fun `official links are not flagged`() {
        assertEquals(0, text("Your SBI Card statement is ready. View at https://www.sbicard.com/statement").riskScore)
        assertEquals(0, text("Your Amazon order has shipped. Track: https://amzn.in/d/abc123").riskScore)
    }

    // ---- URL events & contract --------------------------------------------------------------

    @Test
    fun `URL event with look-alike domain is HIGH phishing`() {
        val r = analyzer.analyze(TestEvents.url("http://hdfcbnak.com/login"))
        assertTrue(r.riskLevel >= RiskLevel.HIGH)
        assertEquals(Category.PHISHING, r.category)
    }

    @Test
    fun `official URL scores zero with MODERATE evidence because reputation is unavailable`() {
        val r = analyzer.analyze(TestEvents.url("https://www.hdfcbank.com"))
        assertEquals(0, r.riskScore)
        assertEquals(EvidenceQuality.MODERATE, r.evidenceQuality)
        assertTrue(r.checks.any { it.name == "Link reputation" && it.status == CheckStatus.UNAVAILABLE })
    }

    @Test
    fun `unparseable URL input records a failed check instead of a clean result`() {
        val r = analyzer.analyze(TestEvents.url("not a link"))
        assertTrue(r.checks.any { it.status == CheckStatus.FAILED })
        assertEquals(EvidenceQuality.LOW, r.evidenceQuality)
    }

    @Test
    fun `result carries id, version and timestamp`() {
        val r = text("hello")
        assertEquals("test", r.eventId)
        assertEquals("0.1-local", r.scoringVersion)
        assertEquals(1_000L, r.analyzedAt)
    }

    @Test
    fun `a crashing detector is reported as FAILED and does not break analysis`() {
        val broken = object : Detector {
            override val name = "Broken"
            override val supportedTypes = setOf(EventType.MANUAL_TEXT_EVENT)
            override fun detect(context: DetectionContext): DetectorOutput = error("boom")
        }
        val r = Analyzer(detectors = Analyzer.defaultDetectors() + broken).analyze(TestEvents.text("share your OTP now"))
        assertTrue(r.checks.any { it.name == "Broken" && it.status == CheckStatus.FAILED })
        assertTrue(r.riskScore >= 60)
    }

    @Test
    fun `huge input is bounded`() {
        val r = text("urgent ".repeat(5_000))
        assertTrue(r.riskScore <= 39)
    }
}
