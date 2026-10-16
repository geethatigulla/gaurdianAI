package ai.guardian.engine.extract

/** A link deliberately not checked, e.g. a local development address or a site the user trusts. */
data class SkippedUrl(val url: ParsedUrl, val reason: String)

data class ExtractedEntities(
    val urls: List<ParsedUrl> = emptyList(),
    val phones: List<String> = emptyList(),
    val upiIds: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val skippedUrls: List<SkippedUrl> = emptyList(),
) {
    val isEmpty: Boolean get() = urls.isEmpty() && phones.isEmpty() && upiIds.isEmpty() && emails.isEmpty()

    companion object {
        val NONE = ExtractedEntities()
    }
}

object PhoneExtractor {
    private val candidateRegex = Regex("(?<![\\w+/.=])\\+?\\d[\\d\\s-]{7,16}\\d(?![\\w/])")

    /**
     * Returns normalised numbers ("+919876543210" or "18001234567"). Only 10–13 digit runs count,
     * which excludes OTPs, amounts and 16-digit card numbers.
     */
    fun extract(text: String): List<String> =
        candidateRegex.findAll(text)
            .map { it.value }
            .mapNotNull { raw ->
                val digits = raw.filter { it.isDigit() }
                if (digits.length !in 10..13) null else if (raw.startsWith("+")) "+$digits" else digits
            }
            .distinct()
            .toList()
}

object UpiExtractor {
    // handle@provider where provider has no dot (otherwise it's an e-mail address).
    private val upiRegex = Regex("(?<![\\w.@-])([a-zA-Z0-9][a-zA-Z0-9._-]{1,255})@([a-zA-Z][a-zA-Z0-9]{1,63})(?![\\w@-]|\\.[a-zA-Z0-9])")

    fun extract(text: String): List<String> =
        upiRegex.findAll(text).map { it.value.lowercase() }.distinct().toList()
}

object EmailExtractor {
    private val emailRegex = Regex("(?<![\\w.+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")

    fun extract(text: String): List<String> =
        emailRegex.findAll(text).map { it.value.lowercase() }.distinct().toList()
}

class EntityExtractor {
    fun extractFromText(text: String): ExtractedEntities {
        val urls = UrlExtractor.extract(text)
        // Strip links before looking for numbers so digits inside URLs aren't read as phone numbers.
        var withoutUrls = text
        urls.forEach { withoutUrls = withoutUrls.replace(it.original, " ") }
        return ExtractedEntities(
            urls = urls,
            phones = PhoneExtractor.extract(withoutUrls),
            upiIds = UpiExtractor.extract(text),
            emails = EmailExtractor.extract(withoutUrls),
        )
    }
}
