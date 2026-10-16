package ai.guardian.engine.detect.qr

import ai.guardian.engine.detect.DetectionContext
import ai.guardian.engine.detect.Detector
import ai.guardian.engine.detect.DetectorOutput
import ai.guardian.engine.model.Check
import ai.guardian.engine.model.CheckStatus
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.Family
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.Severity
import java.net.URLDecoder
import java.util.Locale

/** What a decoded QR code actually does when acted on. */
enum class QrKind { WEB_LINK, UPI_PAYMENT, WIFI, PHONE_CALL, SEND_SMS, EMAIL, APP_INTENT, SCRIPT_OR_DATA, CONTACT, TEXT }

object QrClassifier {
    fun classify(payload: String): QrKind {
        val p = payload.trim().lowercase(Locale.ROOT)
        return when {
            p.startsWith("http://") || p.startsWith("https://") -> QrKind.WEB_LINK
            p.startsWith("upi://") -> QrKind.UPI_PAYMENT
            p.startsWith("wifi:") -> QrKind.WIFI
            p.startsWith("tel:") -> QrKind.PHONE_CALL
            p.startsWith("smsto:") || p.startsWith("sms:") || p.startsWith("mmsto:") -> QrKind.SEND_SMS
            p.startsWith("mailto:") || p.startsWith("matmsg:") -> QrKind.EMAIL
            p.startsWith("intent:") || p.startsWith("market:") -> QrKind.APP_INTENT
            p.startsWith("javascript:") || p.startsWith("data:") || p.startsWith("file:") || p.startsWith("vbscript:") -> QrKind.SCRIPT_OR_DATA
            p.startsWith("begin:vcard") || p.startsWith("mecard:") -> QrKind.CONTACT
            else -> QrKind.TEXT
        }
    }

    /** Parses "upi://pay?pa=x@ybl&pn=Name&am=10" into a lowercase-keyed map. */
    fun upiParams(payload: String): Map<String, String> {
        val query = payload.substringAfter('?', "")
        if (query.isEmpty()) return emptyMap()
        return query.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=').lowercase(Locale.ROOT)
            val value = pair.substringAfter('=', "")
            if (key.isEmpty()) null else key to runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        }.toMap()
    }
}

/**
 * QR-specific checks. Links inside the QR are covered by the URL rules and any text by the message
 * rules; this detector adds what only matters for QR codes: what scanning it will *do*.
 */
class QrPayloadDetector : Detector {
    override val name = "QR code contents"
    override val supportedTypes = setOf(EventType.QR_EVENT)

    private val lureInPayee = Regex("\\b(refund|cashback|cash back|prize|reward|lottery|winner|kyc|bonus|gift|claim|receive)\\b")

    override fun detect(context: DetectionContext): DetectorOutput {
        val payload = context.event.rawInput.trim()
        if (payload.isEmpty()) return DetectorOutput(checks = listOf(Check(name, CheckStatus.FAILED, "QR code was empty")))
        val kind = QrClassifier.classify(payload)
        val indicators = mutableListOf<Indicator>()
        var note = describe(kind)

        when (kind) {
            QrKind.UPI_PAYMENT -> {
                val params = QrClassifier.upiParams(payload)
                val payee = params["pn"].orEmpty()
                val payeeId = params["pa"].orEmpty()
                val amount = params["am"]
                note = buildString {
                    append("Scanning this QR makes you PAY")
                    if (!amount.isNullOrBlank()) append(" ₹$amount")
                    append(" to ").append(payeeId.ifBlank { "an unknown UPI ID" })
                    if (payee.isNotBlank()) append(" (\"$payee\")")
                    append(". QR codes are for paying, never for receiving money.")
                }
                val lure = lureInPayee.find((payee + " " + params["tn"].orEmpty()).lowercase(Locale.ROOT))
                if (lure != null) {
                    indicators += indicator(
                        "QR_PAY_DISGUISED_AS_RECEIVE", Family.PAYMENT_LURE, Severity.STRONG,
                        "This payment QR is labelled \"${lure.value}\", but scanning it takes money from you — it does not send you any",
                        payee.ifBlank { params["tn"] },
                    )
                }
            }
            QrKind.SCRIPT_OR_DATA -> indicators += indicator(
                "QR_SCRIPT_PAYLOAD", Family.QR_PAYLOAD, Severity.STRONG,
                "The QR contains script or embedded data instead of a normal link", payload.take(40),
            )
            QrKind.APP_INTENT -> indicators += indicator(
                "QR_APP_INTENT", Family.QR_PAYLOAD, Severity.MEDIUM,
                "The QR tries to open or install an app directly", payload.take(40),
            )
            QrKind.SEND_SMS -> indicators += indicator(
                "QR_SENDS_SMS", Family.QR_PAYLOAD, Severity.MEDIUM,
                "The QR will prepare an SMS to be sent from your number (can be used for paid premium SMS)", payload.take(40),
            )
            QrKind.PHONE_CALL -> indicators += indicator(
                "QR_DIALS_NUMBER", Family.QR_PAYLOAD, Severity.WEAK,
                "The QR will dial a phone number", payload.removePrefix("tel:").take(20),
            )
            QrKind.WIFI -> if (Regex("(?i)(^|;)T:nopass(;|$)").containsMatchIn(payload.removePrefix("WIFI:").removePrefix("wifi:"))) {
                indicators += indicator(
                    "QR_OPEN_WIFI", Family.QR_PAYLOAD, Severity.WEAK,
                    "The QR joins an open Wi-Fi network without a password — others on it may see your traffic", null,
                )
            }
            else -> Unit
        }
        return DetectorOutput(indicators, listOf(Check(name, CheckStatus.COMPLETED, note)))
    }

    private fun describe(kind: QrKind) = when (kind) {
        QrKind.WEB_LINK -> "Contains a web link"
        QrKind.UPI_PAYMENT -> "UPI payment request"
        QrKind.WIFI -> "Wi-Fi network details"
        QrKind.PHONE_CALL -> "Phone number to call"
        QrKind.SEND_SMS -> "Pre-written SMS"
        QrKind.EMAIL -> "E-mail address"
        QrKind.APP_INTENT -> "Opens an app"
        QrKind.SCRIPT_OR_DATA -> "Script or embedded data"
        QrKind.CONTACT -> "Contact card"
        QrKind.TEXT -> "Plain text"
    }

    private fun indicator(type: String, family: String, severity: Severity, description: String, matched: String?) =
        Indicator(type = type, family = family, severity = severity, description = description, source = "rule:QrPayloadDetector", matchedText = matched)
}
