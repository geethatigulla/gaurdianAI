package ai.guardian.engine.detect.qr

import ai.guardian.engine.analyzer
import ai.guardian.engine.model.Category
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.RiskLevel
import ai.guardian.engine.model.SecurityEvent
import ai.guardian.engine.types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class QrPayloadDetectorTest {
    private fun qr(payload: String) =
        analyzer.analyze(SecurityEvent("test", EventType.QR_EVENT, payload, EventSource.QR_CAMERA, 0L))

    @ParameterizedTest
    @CsvSource(
        "https://example.com, WEB_LINK",
        "upi://pay?pa=shop@okaxis, UPI_PAYMENT",
        "WIFI:T:WPA;S:Home;P:secret;;, WIFI",
        "tel:+919876543210, PHONE_CALL",
        "SMSTO:12345:hello, SEND_SMS",
        "javascript:alert(1), SCRIPT_OR_DATA",
        "intent://scan/#Intent;scheme=x;end, APP_INTENT",
        "hello there, TEXT",
    )
    fun `classifies payload kinds`(payload: String, kind: QrKind) {
        assertEquals(kind, QrClassifier.classify(payload))
    }

    @Test
    fun `parses and decodes UPI parameters`() {
        val p = QrClassifier.upiParams("upi://pay?pa=refund.desk@ybl&pn=Cashback%20Refund&am=4999&cu=INR")
        assertEquals("refund.desk@ybl", p["pa"])
        assertEquals("Cashback Refund", p["pn"])
        assertEquals("4999", p["am"])
    }

    @Test
    fun `ordinary shop UPI QR has no warning signs but explains it is a payment`() {
        val r = qr("upi://pay?pa=sharmastores@okaxis&pn=Sharma%20Stores")
        assertEquals(0, r.riskScore)
        assertTrue(r.checks.any { it.note?.contains("PAY") == true })
        assertTrue(r.checks.any { it.name == "UPI ID reputation" })
    }

    @Test
    fun `UPI QR labelled as refund or cashback is HIGH payment fraud`() {
        val r = qr("upi://pay?pa=claimdesk@ybl&pn=Cashback%20Refund&am=4999")
        assertTrue("QR_PAY_DISGUISED_AS_RECEIVE" in r.indicators.types())
        assertTrue(r.riskLevel >= RiskLevel.HIGH)
        assertEquals(Category.PAYMENT_FRAUD, r.category)
    }

    @Test
    fun `script payload is strong`() {
        val r = qr("javascript:document.location='http://evil.xyz'")
        assertTrue("QR_SCRIPT_PAYLOAD" in r.indicators.types())
        assertEquals(Category.SUSPICIOUS_QR, r.category)
    }

    @Test
    fun `QR link gets the URL rules`() {
        val r = qr("http://hdfcbnak.com/login")
        assertTrue("LOOKALIKE_DOMAIN" in r.indicators.types())
    }

    @Test
    fun `QR text gets the message rules`() {
        val r = qr("Scan done! Now share the OTP you receive with our agent to get your prize")
        assertTrue("SECRET_REQUEST" in r.indicators.types())
    }

    @Test
    fun `open wifi and premium SMS are flagged`() {
        assertTrue("QR_OPEN_WIFI" in qr("WIFI:T:nopass;S:FreeAirportWiFi;;").indicators.types())
        assertTrue("QR_SENDS_SMS" in qr("SMSTO:56789:SUBSCRIBE").indicators.types())
    }
}
