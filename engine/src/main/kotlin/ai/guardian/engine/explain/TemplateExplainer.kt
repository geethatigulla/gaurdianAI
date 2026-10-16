package ai.guardian.engine.explain

import ai.guardian.engine.model.Category
import ai.guardian.engine.model.Check
import ai.guardian.engine.model.CheckStatus
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.RiskLevel
import ai.guardian.engine.risk.RiskAssessment

data class Explanation(val summary: String, val recommendation: String)

/** Turns an assessment into plain English. An LLM explainer (v0.2) would implement this too. */
interface Explainer {
    fun explain(eventType: EventType, assessment: RiskAssessment, indicators: List<Indicator>, checks: List<Check>): Explanation
}

/**
 * Deterministic, template-based explanations. Every sentence is grounded in an indicator or check —
 * nothing is invented.
 */
class TemplateExplainer : Explainer {

    override fun explain(eventType: EventType, assessment: RiskAssessment, indicators: List<Indicator>, checks: List<Check>): Explanation {
        val reputationMissing = checks.any { it.status == CheckStatus.UNAVAILABLE }
        return Explanation(
            summary = summary(eventType, assessment, indicators, reputationMissing),
            recommendation = recommendation(assessment),
        )
    }

    private fun summary(eventType: EventType, assessment: RiskAssessment, indicators: List<Indicator>, reputationMissing: Boolean): String {
        val subject = subjectFor(eventType)
        if (indicators.isEmpty()) {
            val suffix = if (reputationMissing) " External reputation was not checked." else ""
            return "No known warning signs found in this $subject. This is not proof that it is safe — Guardian only " +
                "checks for common scam patterns on your device.$suffix"
        }

        // Strongest first; one sentence per family so correlated signals aren't repeated.
        val topReasons = indicators
            .sortedByDescending { it.severity }
            .distinctBy { it.family }
            .take(3)
            .joinToString(" ") { it.description.trimEnd('.') + "." }

        val opener = when (assessment.level) {
            RiskLevel.CRITICAL -> "This $subject shows strong signs of a scam (${Category.displayName(assessment.category).lowercase()})."
            RiskLevel.HIGH -> "This $subject is likely a scam (${Category.displayName(assessment.category).lowercase()})."
            RiskLevel.MODERATE -> "This $subject has several warning signs."
            RiskLevel.GUARDED -> "This $subject has a few warning signs."
            RiskLevel.LOW -> "Only weak warning signs were found in this $subject."
        }
        val caveat = if (reputationMissing) " External reputation was not checked." else ""
        return "$opener $topReasons$caveat"
    }

    private fun recommendation(assessment: RiskAssessment): String {
        val categoryAdvice = when (assessment.category) {
            Category.PAYMENT_FRAUD -> "You never need to enter your UPI PIN or scan a QR code to receive money."
            Category.MALWARE_LURE -> "Do not install apps from links or files — only use the Google Play Store."
            Category.REMOTE_ACCESS_SCAM -> "Never install screen-sharing apps (AnyDesk, TeamViewer, etc.) because someone asked you to. If you already did, uninstall it and call your bank."
            Category.AUTHORITY_SCAM -> "Police, CBI and customs do not arrest people over video calls or ask for money to close a case. Hang up."
            Category.PHISHING -> "Don't use this link. Open your bank's or the company's official app, or type their website yourself."
            Category.PRIZE_SCAM -> "You cannot win a lottery or prize you never entered. Don't pay any fee to claim it."
            Category.JOB_SCAM -> "Genuine jobs never ask you to pay money to earn money."
            Category.SUSPICIOUS_LINK, Category.SUSPICIOUS_QR -> "Don't open this link unless you trust the source and can confirm the address."
            else -> null
        }
        val secrets = "Never share OTP, UPI PIN, CVV or passwords with anyone."
        val alreadyActed = "If you already paid or shared details, call your bank now and report it at 1930 or cybercrime.gov.in."

        return when (assessment.level) {
            RiskLevel.CRITICAL, RiskLevel.HIGH ->
                listOfNotNull("Do not click links, reply, call back or pay.", categoryAdvice, secrets, alreadyActed).joinToString(" ")
            RiskLevel.MODERATE ->
                listOfNotNull("Be very careful. Verify with the sender through an official channel before doing anything.", categoryAdvice, alreadyActed).joinToString(" ")
            RiskLevel.GUARDED ->
                listOfNotNull("Some warning signs. Verify through an official website, app or phone number before acting.", categoryAdvice).joinToString(" ")
            RiskLevel.LOW ->
                if (assessment.category == Category.NO_KNOWN_INDICATORS) {
                    "Still stay alert: if it asks for money, OTP or personal details, verify through official channels first."
                } else {
                    "Only weak signs were found. Stay cautious and verify if it asks for money or personal details."
                }
        }
    }

    private fun subjectFor(eventType: EventType) = when (eventType) {
        EventType.SMS_EVENT, EventType.APP_MESSAGE_EVENT, EventType.MANUAL_TEXT_EVENT -> "message"
        EventType.URL_EVENT -> "link"
        EventType.QR_EVENT -> "QR code"
        EventType.CALL_EVENT -> "phone number"
        EventType.IP_EVENT -> "IP address"
        EventType.DOMAIN_EVENT -> "domain"
    }
}
