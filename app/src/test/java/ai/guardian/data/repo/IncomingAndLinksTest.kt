package ai.guardian.data.repo

import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.SecurityEvent
import ai.guardian.platform.incoming.IncomingRequest
import ai.guardian.platform.incoming.IncomingRequests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingAndLinksTest {
    private fun event(type: EventType, input: String, sender: String? = null, channel: String? = null) =
        SecurityEvent("id", type, input, EventSource.MANUAL, 0L, sender, channel)

    @Test
    fun `shared lone link is checked as a link, anything else as a message`() {
        val link = IncomingRequests.analyzeText("  hdfcbnak.com/login ", EventSource.SHARE_INTENT) as IncomingRequest.Analyze
        assertEquals(EventType.URL_EVENT, link.type)
        val msg = IncomingRequests.analyzeText("Your KYC is pending, visit hdfcbnak.com", EventSource.SHARE_INTENT) as IncomingRequest.Analyze
        assertEquals(EventType.MANUAL_TEXT_EVENT, msg.type)
        assertTrue(IncomingRequests.analyzeText("   ", EventSource.CLIPBOARD) is IncomingRequest.Error)
    }

    @Test
    fun `only plain web links are offered for opening`() {
        assertEquals("https://example.com/a", OpenableUrl.forEvent(event(EventType.URL_EVENT, "example.com/a")))
        assertEquals("http://x.top", OpenableUrl.forEvent(event(EventType.QR_EVENT, "http://x.top")))
        assertNull(OpenableUrl.forEvent(event(EventType.QR_EVENT, "upi://pay?pa=a@ybl")))
        assertNull(OpenableUrl.forEvent(event(EventType.QR_EVENT, "javascript:alert(1)")))
        assertNull(OpenableUrl.forEvent(event(EventType.MANUAL_TEXT_EVENT, "https://example.com")))
    }

    @Test
    fun `app message label names the app and sender but not the text`() {
        val label = DisplayLabel.forEvent(event(EventType.APP_MESSAGE_EVENT, "share your OTP 482913", sender = "Rahul", channel = "WhatsApp"))
        assertEquals("WhatsApp from Rahul · 21 chars", label)
    }
}
