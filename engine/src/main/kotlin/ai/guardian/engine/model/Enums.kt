package ai.guardian.engine.model

/** APP_MESSAGE_EVENT = a message read from another app's notification (WhatsApp, Telegram, Gmail…). */
enum class EventType { SMS_EVENT, APP_MESSAGE_EVENT, MANUAL_TEXT_EVENT, URL_EVENT, QR_EVENT, CALL_EVENT, IP_EVENT, DOMAIN_EVENT }

/** Where the input came from. Informational only — never changes scoring. */
enum class EventSource { MANUAL, SHARE_INTENT, PROCESS_TEXT, CLIPBOARD, LINK_HANDLER, QR_CAMERA, QR_IMAGE, SMS_RECEIVER, NOTIFICATION, SCREEN, BROWSER }

/**
 * UI bands over the 0–100 score. These are categories, not calibrated probabilities.
 * Bands: 0-19, 20-39, 40-59, 60-79, 80-100.
 */
enum class RiskLevel(val minScore: Int) {
    LOW(0), GUARDED(20), MODERATE(40), HIGH(60), CRITICAL(80);

    companion object {
        fun fromScore(score: Int): RiskLevel {
            val clamped = score.coerceIn(0, 100)
            return entries.last { clamped >= it.minScore }
        }
    }
}

/** How much of the analysis we could actually run. Independent of the risk score. */
enum class EvidenceQuality { LOW, MODERATE, HIGH }

enum class CheckStatus { COMPLETED, UNAVAILABLE, SKIPPED, FAILED }

enum class Severity { WEAK, MEDIUM, STRONG }
