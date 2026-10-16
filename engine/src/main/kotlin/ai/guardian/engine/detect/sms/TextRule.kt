package ai.guardian.engine.detect.sms

import ai.guardian.engine.extract.ExtractedEntities
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.SecurityEvent
import ai.guardian.engine.model.Severity
import java.util.Locale

data class TextContext(
    val event: SecurityEvent,
    val original: String,
    /** Lowercased, zero-width characters removed, whitespace collapsed. Rules match against this. */
    val normalized: String,
    val extracted: ExtractedEntities,
)

interface TextRule {
    val id: String
    fun evaluate(context: TextContext): List<Indicator>
}

object TextNormalizer {
    private val zeroWidth = Regex("[\\u200B-\\u200D\\u2060\\uFEFF\\u00AD]")
    private val whitespace = Regex("\\s+")

    fun normalize(text: String): String =
        text.replace(zeroWidth, "")
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .lowercase(Locale.ROOT)
            .replace(whitespace, " ")
            .trim()
}

data class PatternSpec(
    val type: String,
    val severity: Severity,
    val description: String,
    val regex: Regex,
)

/**
 * A rule made of independent regex patterns. Each pattern fires at most once. When
 * [negationAware] is set, a match preceded by "do not", "never" etc. is ignored, so a genuine bank
 * warning ("never share your OTP") does not trip the rule it is warning about.
 */
abstract class PatternTextRule(
    final override val id: String,
    private val family: String,
    private val negationAware: Boolean = false,
) : TextRule {
    protected abstract val patterns: List<PatternSpec>

    override fun evaluate(context: TextContext): List<Indicator> = patterns.mapNotNull { spec ->
        val match = spec.regex.findAll(context.normalized).firstOrNull { !negationAware || !isNegated(context.normalized, it.range.first) }
            ?: return@mapNotNull null
        Indicator(
            type = spec.type,
            family = family,
            severity = spec.severity,
            description = spec.description,
            source = "rule:$id",
            matchedText = match.value.trim().take(MAX_MATCH_LENGTH),
        )
    }

    companion object {
        const val MAX_MATCH_LENGTH = 80
        private val negation = Regex("\\b(do not|don't|dont|never|not to|no one|nobody|will not|won't|not share|not ask)\\b")

        fun isNegated(text: String, matchStart: Int): Boolean {
            val windowStart = (matchStart - 25).coerceAtLeast(0)
            return negation.containsMatchIn(text.substring(windowStart, matchStart))
        }
    }
}

/** Shorthand used by rule definitions. `G` = gap of up to n chars within the same sentence. */
internal fun gap(max: Int) = "[^.!?\\n]{0,$max}"
