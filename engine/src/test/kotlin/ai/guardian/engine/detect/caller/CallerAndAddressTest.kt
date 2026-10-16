package ai.guardian.engine.detect.caller

import ai.guardian.engine.analyzer
import ai.guardian.engine.model.Category
import ai.guardian.engine.model.CheckStatus
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.RiskLevel
import ai.guardian.engine.model.SecurityEvent
import ai.guardian.engine.types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallerAndAddressTest {
    private fun call(number: String, reported: Set<String> = emptySet()) =
        analyzer.analyze(SecurityEvent("t", EventType.CALL_EVENT, number, EventSource.MANUAL, 0L), reportedNumbers = reported)

    private fun address(type: EventType, value: String) =
        analyzer.analyze(SecurityEvent("t", type, value, EventSource.MANUAL, 0L))

    @Test
    fun `normalises Indian numbers in any format`() {
        assertEquals("+919876543210", PhoneNumbers.normalize("98765 43210"))
        assertEquals("+919876543210", PhoneNumbers.normalize("+91-98765-43210"))
        assertEquals("+919876543210", PhoneNumbers.normalize("tel:+919876543210"))
    }

    @Test
    fun `valid Indian mobile has no warning signs but says reputation is unavailable`() {
        val r = call("+91 98765 43210")
        assertEquals(0, r.riskScore)
        assertTrue(r.checks.any { it.note?.contains("Valid Indian mobile") == true })
        assertTrue(r.checks.any { it.name == "Phone number reputation" && it.status == CheckStatus.UNAVAILABLE })
    }

    @Test
    fun `international number is a weak sign and names the country`() {
        val r = call("+92 300 1234567")
        assertEquals(setOf("INTERNATIONAL_NUMBER"), r.indicators.types())
        assertTrue(r.indicators.single().description.contains("Pakistan"))
        assertEquals(RiskLevel.LOW, r.riskLevel)
    }

    @Test
    fun `invalid number is flagged as possibly spoofed`() {
        val r = call("+91 12345")
        assertTrue(r.indicators.types().any { it == "INVALID_NUMBER" || it == "UNREADABLE_NUMBER" }, "got ${r.indicators}")
    }

    @Test
    fun `business sender IDs are not treated as phone numbers`() {
        assertEquals(0, call("VM-SBIINB").riskScore)
    }

    @Test
    fun `reported number is HIGH regardless of format`() {
        val r = call("098765 43210", reported = setOf("+91 98765 43210"))
        assertTrue("REPORTED_NUMBER" in r.indicators.types())
        assertTrue(r.riskLevel >= RiskLevel.HIGH)
        assertEquals(Category.SUSPICIOUS_NUMBER, r.category)
    }

    @Test
    fun `SMS from a reported sender is flagged`() {
        val r = analyzer.analyze(
            SecurityEvent("t", EventType.SMS_EVENT, "Hello, call me back", EventSource.SMS_RECEIVER, 0L, sender = "+919876543210"),
            reportedNumbers = setOf("9876543210"),
        )
        assertTrue("REPORTED_NUMBER" in r.indicators.types())
    }

    @Test
    fun `IP addresses are classified honestly without a made-up score`() {
        assertTrue(address(EventType.IP_EVENT, "192.168.1.1").checks.any { it.note?.startsWith("Private") == true })
        assertTrue(address(EventType.IP_EVENT, "203.0.113.5").checks.any { it.note?.startsWith("Documentation") == true })
        assertTrue(address(EventType.IP_EVENT, "8.8.8.8").checks.any { it.note?.startsWith("Public IPv4") == true })
        assertEquals(0, address(EventType.IP_EVENT, "8.8.8.8").riskScore)
        assertTrue(address(EventType.IP_EVENT, "999.1.1.1").checks.any { it.status == CheckStatus.FAILED })
    }

    @Test
    fun `domains get the link rules`() {
        val r = address(EventType.DOMAIN_EVENT, "hdfcbnak.com")
        assertTrue("LOOKALIKE_DOMAIN" in r.indicators.types())
        assertEquals(Category.PHISHING, r.category)
        assertEquals(0, address(EventType.DOMAIN_EVENT, "hdfcbank.com").riskScore)
    }

    @Test
    fun `suspicious-only domain is categorised as an address`() {
        assertEquals(Category.SUSPICIOUS_ADDRESS, address(EventType.DOMAIN_EVENT, "free-gifts.top").category)
    }
}
