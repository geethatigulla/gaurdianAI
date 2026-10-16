package ai.guardian.engine

import ai.guardian.engine.detect.sms.TextContext
import ai.guardian.engine.detect.sms.TextNormalizer
import ai.guardian.engine.extract.EntityExtractor
import ai.guardian.engine.extract.UrlParser
import ai.guardian.engine.extract.ParsedUrl
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.SecurityEvent

object TestEvents {
    fun text(body: String, sender: String? = null, type: EventType = EventType.MANUAL_TEXT_EVENT) =
        SecurityEvent(id = "test", type = type, rawInput = body, source = EventSource.MANUAL, timestamp = 0L, sender = sender)

    fun url(address: String) =
        SecurityEvent(id = "test", type = EventType.URL_EVENT, rawInput = address, source = EventSource.MANUAL, timestamp = 0L)

    fun textContext(body: String, sender: String? = null): TextContext {
        val event = text(body, sender, if (sender != null) EventType.SMS_EVENT else EventType.MANUAL_TEXT_EVENT)
        return TextContext(event, body, TextNormalizer.normalize(body), EntityExtractor().extractFromText(body))
    }

    fun parsed(url: String): ParsedUrl = requireNotNull(UrlParser.parse(url)) { "Could not parse $url" }
}

val analyzer = Analyzer(clock = { 1_000L })

fun List<Indicator>.types(): Set<String> = map { it.type }.toSet()
