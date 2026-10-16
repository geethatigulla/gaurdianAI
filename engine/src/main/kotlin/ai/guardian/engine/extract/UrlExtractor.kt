package ai.guardian.engine.extract

/**
 * Finds web addresses in free text: explicit http(s) links, "www." links, bare domains with a
 * recognised TLD ("sbi-kyc.xyz/login") and bare IPv4 links with a path or port ("45.9.1.2/pay").
 */
object UrlExtractor {
    private const val TLDS =
        "com|net|org|in|co|io|info|biz|xyz|top|club|online|site|live|app|link|ly|me|cc|tk|ml|ga|cf|gq|" +
            "ru|cn|shop|store|click|icu|vip|win|bid|loan|work|today|support|help|buzz|rest|cyou|fun|" +
            "space|website|tech|pro|us|uk|gov|edu|bank|pw|su|ws|to|gl|gd|sh|page|dev|sbi"

    private val urlRegex = Regex(
        "(?i)" +
            "(?:\\b(?:https?|ftp)://[^\\s<>\"'`]+)" +
            "|(?:(?<![@\\w.-])www\\.[^\\s<>\"'`]+)" +
            "|(?:(?<![@\\w.:/-])\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d{1,5})?/[^\\s<>\"'`]*)" +
            "|(?:(?<![@\\w.:/-])\\d{1,3}(?:\\.\\d{1,3}){3}:\\d{1,5}(?![\\w.]))" +
            "|(?:(?<![@\\w.-])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+(?:$TLDS)(?![a-z0-9-])(?:[/?#][^\\s<>\"'`]*)?)",
    )

    private const val TRAILING_PUNCTUATION = ".,;:!?)]}'\"*>"

    fun extract(text: String): List<ParsedUrl> =
        urlRegex.findAll(text)
            .map { trimTrailing(it.value) }
            .mapNotNull { UrlParser.parse(it) }
            .distinctBy { it.original.lowercase() }
            .toList()

    /** Extract from a single user-entered address, tolerating a missing scheme. */
    fun parseSingle(input: String): ParsedUrl? {
        val trimmed = trimTrailing(input.trim())
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return extract(input).firstOrNull()
        val parsed = UrlParser.parse(trimmed) ?: return null
        // A single word with no dot and no scheme ("hello") is not a web address.
        if (parsed.scheme == null && !parsed.host.contains('.') && !parsed.isIpv6Host) return null
        return parsed
    }

    private fun trimTrailing(value: String): String {
        var end = value.length
        while (end > 0 && value[end - 1] in TRAILING_PUNCTUATION) {
            // Keep a closing paren if the URL itself opened one, e.g. wiki links.
            if (value[end - 1] == ')' && value.substring(0, end - 1).count { it == '(' } > value.substring(0, end - 1).count { it == ')' }) break
            end--
        }
        return value.substring(0, end)
    }
}
