package ai.guardian.data.repo

import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.SecurityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DisplayLabelTest {
    private fun event(type: EventType, input: String, sender: String? = null) =
        SecurityEvent("id", type, input, EventSource.MANUAL, 0L, sender)

    @Test
    fun `message label never contains the message text`() {
        val secret = "Your OTP is 482913, share it with me"
        val label = DisplayLabel.forEvent(event(EventType.MANUAL_TEXT_EVENT, secret))
        assertEquals("Message · ${secret.length} chars", label)
        assertFalse(label.contains("482913"))
    }

    @Test
    fun `message with a link shows only the host`() {
        val label = DisplayLabel.forEvent(event(EventType.MANUAL_TEXT_EVENT, "Pay now at http://sbi-kyc.xyz/login?acct=123"))
        assertEquals("Message · link to sbi-kyc.xyz", label)
    }

    @Test
    fun `url and sms labels`() {
        assertEquals("Link · hdfcbnak.com", DisplayLabel.forEvent(event(EventType.URL_EVENT, "http://hdfcbnak.com/login")))
        assertEquals("SMS from VM-SBIINB · 5 chars", DisplayLabel.forEvent(event(EventType.SMS_EVENT, "hello", sender = "VM-SBIINB")))
    }
}
