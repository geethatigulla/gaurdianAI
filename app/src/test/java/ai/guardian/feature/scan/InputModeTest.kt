package ai.guardian.feature.scan

import ai.guardian.engine.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Test

class InputModeTest {
    @Test
    fun `IP or domain input picks the right event type`() {
        assertEquals(EventType.IP_EVENT, InputMode.ADDRESS.eventTypeFor("192.168.1.1"))
        assertEquals(EventType.IP_EVENT, InputMode.ADDRESS.eventTypeFor(" [2001:db8::1] "))
        assertEquals(EventType.DOMAIN_EVENT, InputMode.ADDRESS.eventTypeFor("example.com"))
    }

    @Test
    fun `other modes map directly`() {
        assertEquals(EventType.MANUAL_TEXT_EVENT, InputMode.TEXT.eventTypeFor("anything"))
        assertEquals(EventType.URL_EVENT, InputMode.URL.eventTypeFor("example.com"))
        assertEquals(EventType.CALL_EVENT, InputMode.PHONE.eventTypeFor("+919876543210"))
    }
}
