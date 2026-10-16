package ai.guardian.engine.model

/**
 * Indicator families. Indicators in the same family are treated as correlated — only the
 * strongest one per family contributes to the score. See docs/risk-scoring.md.
 */
object Family {
    // Message content
    const val URGENCY = "URGENCY"
    const val ACCOUNT_THREAT = "ACCOUNT_THREAT"
    const val AUTHORITY_THREAT = "AUTHORITY_THREAT"
    const val CREDENTIAL_REQUEST = "CREDENTIAL_REQUEST"
    const val IMPERSONATION = "IMPERSONATION"
    const val PAYMENT_LURE = "PAYMENT_LURE"
    const val REWARD_LURE = "REWARD_LURE"
    const val JOB_LURE = "JOB_LURE"
    const val APK_LURE = "APK_LURE"
    const val REMOTE_ACCESS_LURE = "REMOTE_ACCESS_LURE"

    // Links
    const val URL_OBFUSCATION = "URL_OBFUSCATION"
    const val URL_IMPERSONATION = "URL_IMPERSONATION"
    const val URL_STRUCTURE = "URL_STRUCTURE"
    const val URL_TRANSPORT = "URL_TRANSPORT"

    // Other event types
    const val QR_PAYLOAD = "QR_PAYLOAD"
    const val NUMBER_FORMAT = "NUMBER_FORMAT"
    const val NETWORK_ADDRESS = "NETWORK_ADDRESS"
}

/** Result categories shown to the user. */
object Category {
    const val NO_KNOWN_INDICATORS = "NO_KNOWN_INDICATORS"
    const val PHISHING = "PHISHING"
    const val PAYMENT_FRAUD = "PAYMENT_FRAUD"
    const val MALWARE_LURE = "MALWARE_LURE"
    const val REMOTE_ACCESS_SCAM = "REMOTE_ACCESS_SCAM"
    const val AUTHORITY_SCAM = "AUTHORITY_SCAM"
    const val PRIZE_SCAM = "PRIZE_SCAM"
    const val JOB_SCAM = "JOB_SCAM"
    const val SUSPICIOUS_LINK = "SUSPICIOUS_LINK"
    const val SUSPICIOUS_QR = "SUSPICIOUS_QR"
    const val SUSPICIOUS_NUMBER = "SUSPICIOUS_NUMBER"
    const val SUSPICIOUS_ADDRESS = "SUSPICIOUS_ADDRESS"
    const val SUSPICIOUS_MESSAGE = "SUSPICIOUS_MESSAGE"

    fun displayName(category: String): String = when (category) {
        NO_KNOWN_INDICATORS -> "No known warning signs"
        PHISHING -> "Possible phishing"
        PAYMENT_FRAUD -> "Possible payment fraud"
        MALWARE_LURE -> "Possible malicious app download"
        REMOTE_ACCESS_SCAM -> "Possible remote-access scam"
        AUTHORITY_SCAM -> "Possible fake-authority scam"
        PRIZE_SCAM -> "Possible prize / reward scam"
        JOB_SCAM -> "Possible job / task scam"
        SUSPICIOUS_LINK -> "Suspicious link"
        SUSPICIOUS_QR -> "Suspicious QR code"
        SUSPICIOUS_NUMBER -> "Unusual phone number"
        SUSPICIOUS_ADDRESS -> "Unusual network address"
        SUSPICIOUS_MESSAGE -> "Suspicious message"
        else -> category.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
}
