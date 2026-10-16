package ai.guardian.engine.privacy

/**
 * Masks secrets before raw text is ever stored (only when the user opts in to storing raw text).
 * Links are kept — they are the evidence. OTP-like codes, card/account numbers and e-mail local
 * parts are masked; phone numbers keep only their first and last two digits.
 */
object Redactor {
    private val cardOrAccount = Regex("(?<!\\d)(?:\\d[ -]?){11,18}\\d(?!\\d)")
    private val phone = Regex("(?<![\\w])(\\+?\\d{2})\\d{6,9}(\\d{2})(?!\\d)")
    private val shortCode = Regex("(?<![\\d₹.,])(?<!rs\\.? ?)(?<!inr ?)(?<!₹ )\\b\\d{4,8}\\b(?![.,]\\d)", RegexOption.IGNORE_CASE)
    private val email = Regex("\\b([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b")

    fun redact(text: String): String =
        text
            .replace(email) { "${it.groupValues[1]}***@${it.groupValues[2]}" }
            .replace(phone) { "${it.groupValues[1]}******${it.groupValues[2]}" }
            .replace(cardOrAccount, "[number hidden]")
            .replace(shortCode, "[code hidden]")
}
