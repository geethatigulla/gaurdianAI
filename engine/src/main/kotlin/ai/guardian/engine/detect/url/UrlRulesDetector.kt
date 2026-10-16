package ai.guardian.engine.detect.url

import ai.guardian.engine.detect.DetectionContext
import ai.guardian.engine.detect.Detector
import ai.guardian.engine.detect.DetectorOutput
import ai.guardian.engine.model.Check
import ai.guardian.engine.model.CheckStatus
import ai.guardian.engine.model.EventType

/** Runs every URL rule against every link found in the event (capped, to bound work). */
class UrlRulesDetector(private val rules: List<UrlRule> = defaultRules()) : Detector {
    override val name = "Link structure & look-alike check"
    override val supportedTypes = setOf(EventType.SMS_EVENT, EventType.APP_MESSAGE_EVENT, EventType.MANUAL_TEXT_EVENT, EventType.URL_EVENT, EventType.QR_EVENT)

    override fun detect(context: DetectionContext): DetectorOutput {
        val urls = context.extracted.urls.take(MAX_URLS)
        if (urls.isEmpty()) {
            return if (context.event.type == EventType.URL_EVENT && context.extracted.skippedUrls.isEmpty()) {
                DetectorOutput(checks = listOf(Check(name, CheckStatus.FAILED, "Couldn't read a web address from the input")))
            } else {
                DetectorOutput.EMPTY // No links: this check simply doesn't apply.
            }
        }
        val indicators = urls
            .flatMap { url -> rules.flatMap { it.evaluate(url) } }
            .distinctBy { it.type to it.matchedText }
        val note = if (urls.size == 1) "1 link checked" else "${urls.size} links checked"
        return DetectorOutput(indicators, listOf(Check(name, CheckStatus.COMPLETED, note)))
    }

    companion object {
        const val MAX_URLS = 10

        fun defaultRules(): List<UrlRule> = listOf(
            IpHostRule(),
            UserInfoRule(),
            ShortenerRule(),
            PunycodeRule(),
            LookalikeDomainRule(),
            BrandInDomainRule(),
            SuspiciousTldRule(),
            UrlShapeRule(),
            LureKeywordRule(),
            InsecureTransportRule(),
            ApkLinkRule(),
        )
    }
}
