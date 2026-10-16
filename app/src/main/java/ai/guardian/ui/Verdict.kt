package ai.guardian.ui

import ai.guardian.engine.model.Category
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.RiskLevel
import ai.guardian.engine.model.RiskResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Short, plain words for a result. Pure presentation of what the engine decided — never changes
 * the score. "No warning signs" is deliberately not "Safe".
 */
data class Verdict(val title: String, val subtitle: String, val icon: ImageVector)

fun RiskResult.verdict(): Verdict {
    val noSigns = indicators.isEmpty()
    return when {
        noSigns -> Verdict("No warning signs", "Not a guarantee it's safe", Icons.Outlined.Info)
        riskLevel == RiskLevel.CRITICAL -> Verdict("Scam alert", Category.displayName(category), Icons.Outlined.GppBad)
        riskLevel == RiskLevel.HIGH -> Verdict("Likely scam", Category.displayName(category), Icons.Outlined.GppBad)
        riskLevel == RiskLevel.MODERATE -> Verdict("Suspicious", Category.displayName(category), Icons.Outlined.ReportProblem)
        riskLevel == RiskLevel.GUARDED -> Verdict("Be careful", Category.displayName(category), Icons.Outlined.GppMaybe)
        else -> Verdict("Low risk", "Only weak signs found", Icons.Outlined.Info)
    }
}

/** Splits the engine's recommendation into short bullet points. */
fun RiskResult.actionBullets(): List<String> =
    recommendation.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }

/** A 2–5 word title for each warning sign; the full engine description is shown on tap. */
fun Indicator.shortTitle(): String = when (type) {
    "URGENT_LANGUAGE" -> "Rushes you"
    "SHORT_DEADLINE" -> "Very short deadline"
    "ACCOUNT_BLOCK_THREAT" -> "Threatens to block account"
    "KYC_PRESSURE" -> "KYC update pressure"
    "UTILITY_DISCONNECTION" -> "Threatens power cut"
    "DIGITAL_ARREST" -> "Fake \"digital arrest\""
    "LEGAL_THREAT" -> "Police / legal threats"
    "SECRET_REQUEST" -> "Asks for OTP, PIN or password"
    "VERIFY_ACCOUNT_REQUEST" -> "Asks to verify your account"
    "ID_DOCUMENT_REQUEST" -> "Asks for ID documents"
    "BRAND_LINK_MISMATCH" -> "Link is not the real website"
    "PERSONAL_NUMBER_SENDER" -> "Sent from a personal number"
    "AUTHORITY_MENTION" -> "Claims to be officials"
    "PIN_TO_RECEIVE" -> "PIN or QR to \"receive\" money"
    "COLLECT_REQUEST" -> "Payment request"
    "ACCIDENTAL_TRANSFER" -> "\"Sent by mistake\" trick"
    "ADVANCE_FEE" -> "Asks for a fee first"
    "REFUND_LURE" -> "Fake refund"
    "PAY_TO_UPI_ID" -> "Asks you to pay a UPI ID"
    "PRIZE_CLAIM" -> "Fake prize or lottery"
    "REWARD_POINTS_EXPIRY" -> "Fake reward points"
    "EASY_MONEY_JOB" -> "Easy-money job offer"
    "APK_FILE", "APK_DOWNLOAD_LINK" -> "Unsafe app download"
    "INSTALL_FROM_LINK" -> "Install an app from a link"
    "REMOTE_ACCESS_APP" -> "Remote-control app"
    "SCREEN_SHARE_REQUEST" -> "Asks to share screen"
    "IP_AS_HOST" -> "Link hides its address"
    "ENCODED_IP_HOST", "AT_SIGN_IN_URL" -> "Disguised link"
    "URL_SHORTENER" -> "Shortened link"
    "PUNYCODE_HOST", "MIXED_SCRIPT_HOST" -> "Fake letters in address"
    "LOOKALIKE_DOMAIN" -> "Look-alike website"
    "BRAND_IN_UNRELATED_DOMAIN" -> "Fake brand website"
    "SUSPICIOUS_TLD" -> "Cheap throwaway address"
    "LONG_URL" -> "Unusually long link"
    "MANY_SUBDOMAINS" -> "Confusing address"
    "UNUSUAL_PORT" -> "Unusual network port"
    "LURE_KEYWORDS_IN_URL" -> "Bait words in link"
    "NO_HTTPS" -> "Not a secure link"
    "QR_PAY_DISGUISED_AS_RECEIVE" -> "Fake \"get money\" QR"
    "QR_SCRIPT_PAYLOAD" -> "Hidden code in QR"
    "QR_APP_INTENT" -> "QR opens an app"
    "QR_SENDS_SMS" -> "QR sends an SMS"
    "QR_DIALS_NUMBER" -> "QR dials a number"
    "QR_OPEN_WIFI" -> "Open Wi-Fi network"
    "UNREADABLE_NUMBER", "INVALID_NUMBER" -> "Fake-looking number"
    "INTERNATIONAL_NUMBER" -> "Foreign number"
    "PREMIUM_RATE_NUMBER" -> "Premium-rate number"
    "VOIP_NUMBER" -> "Internet (VoIP) number"
    "REPORTED_NUMBER" -> "You reported this number"
    else -> description.substringBefore(" — ").substringBefore(",").take(40)
}
