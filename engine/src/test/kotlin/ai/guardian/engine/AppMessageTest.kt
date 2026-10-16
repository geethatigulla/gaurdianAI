package ai.guardian.engine

import ai.guardian.engine.model.Category
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.RiskLevel
import ai.guardian.engine.model.SecurityEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Messages read from other apps' notifications go through the same rules as SMS. */
class AppMessageTest {
    private fun whatsapp(body: String, sender: String) =
        analyzer.analyze(SecurityEvent("test", EventType.APP_MESSAGE_EVENT, body, EventSource.NOTIFICATION, 0L, sender, channel = "WhatsApp"))

    @Test
    fun `task scam on WhatsApp is flagged`() {
        val r = whatsapp("Hello! Part time job, earn Rs 5000 daily by rating hotels. Join telegram: t.me/earnfast", "+91 98765 43210")
        assertTrue(r.riskLevel >= RiskLevel.GUARDED)
        assertEquals(Category.JOB_SCAM, r.category)
    }

    @Test
    fun `bank claim from a personal WhatsApp number with a link is HIGH`() {
        val r = whatsapp("SBI: your YONO is blocked, update PAN now at http://sbi-yono-pan.top", "+919876543210")
        assertTrue(r.riskLevel >= RiskLevel.HIGH)
        assertTrue("PERSONAL_NUMBER_SENDER" in r.indicators.types())
    }

    @Test
    fun `ordinary chat is not flagged`() {
        assertEquals(0, whatsapp("Reached home, will call you after dinner", "Amma").riskScore)
    }
}
