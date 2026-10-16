package ai.guardian.data.prefs

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Defaults are the privacy-preserving choice. */
data class UserSettings(
    val onboardingDone: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val storeRawText: Boolean = false,
    /** 0 = don't keep history at all (results are shown once, then discarded). */
    val retentionDays: Int = 30,
    val smsMonitoring: Boolean = false,
    /** Check messages from WhatsApp/Telegram/etc. via notification access. */
    val appMessageMonitoring: Boolean = false,
    /** Show the floating scan button (only visible while the accessibility service is enabled). */
    val floatingButton: Boolean = true,
    /** Check websites opened in the user's own browser (needs the accessibility service). */
    val linkGuard: Boolean = true,
    /** Domains the user trusts (e.g. their own dev/staging sites); links to them are not checked. */
    val trustedDomains: Set<String> = emptySet(),
    /** Numbers the user reported as scams (E.164). */
    val reportedNumbers: Set<String> = emptySet(),
    /** Check incoming calls from unknown numbers (needs the call-screening role). */
    val callScreening: Boolean = true,
    /** Reject calls from reported numbers. Off by default: blocking is the user's explicit choice. */
    val blockReportedCalls: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val alertThreshold: Int = 60,
    val lockScreenPrivacy: Boolean = true,
    val appLock: Boolean = false,
) {
    companion object {
        val RETENTION_OPTIONS = listOf(0, 7, 30, 90, 365)
    }
}
