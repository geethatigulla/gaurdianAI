package ai.guardian.engine.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedactorTest {
    @Test
    fun `masks OTPs but keeps amounts and links`() {
        val out = Redactor.redact("Your OTP is 482913. Pay Rs 5000 at https://sbi-kyc.xyz/login")
        assertFalse(out.contains("482913"))
        assertTrue(out.contains("Rs 5000"))
        assertTrue(out.contains("https://sbi-kyc.xyz/login"))
    }

    @Test
    fun `masks card numbers`() {
        assertEquals("Card [number hidden] blocked", Redactor.redact("Card 4111 1111 1111 1111 blocked"))
    }

    @Test
    fun `partially masks phone numbers and e-mail`() {
        val out = Redactor.redact("Call 9876543210 or mail rahul.k@example.com")
        assertTrue(out.contains("98******10"), out)
        assertTrue(out.contains("r***@example.com"), out)
    }
}
