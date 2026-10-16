package ai.guardian.engine

import ai.guardian.engine.detect.DetectionContext
import ai.guardian.engine.detect.Detector
import ai.guardian.engine.detect.DetectorOutput
import ai.guardian.engine.detect.address.AddressDetector
import ai.guardian.engine.detect.caller.NumberFormatDetector
import ai.guardian.engine.detect.caller.PhoneNumbers
import ai.guardian.engine.detect.caller.ReportedNumberDetector
import ai.guardian.engine.detect.qr.QrPayloadDetector
import ai.guardian.engine.detect.sms.TextRulesDetector
import ai.guardian.engine.detect.url.UrlRulesDetector
import ai.guardian.engine.explain.Explainer
import ai.guardian.engine.explain.TemplateExplainer
import ai.guardian.engine.extract.DomainUtils
import ai.guardian.engine.extract.EntityExtractor
import ai.guardian.engine.extract.ExtractedEntities
import ai.guardian.engine.extract.SkippedUrl
import ai.guardian.engine.extract.UrlExtractor
import ai.guardian.engine.intel.NoopIntelProvider
import ai.guardian.engine.intel.ThreatIntelProvider
import ai.guardian.engine.model.Check
import ai.guardian.engine.model.CheckStatus
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.RiskResult
import ai.guardian.engine.model.SecurityEvent
import ai.guardian.engine.risk.RiskEngine

/**
 * The engine's single entry point: extract → detect → intel → risk → explain.
 * Pure and synchronous; callers should run it off the main thread.
 */
class Analyzer(
    private val detectors: List<Detector> = defaultDetectors(),
    private val intel: ThreatIntelProvider = NoopIntelProvider(),
    private val riskEngine: RiskEngine = RiskEngine(),
    private val explainer: Explainer = TemplateExplainer(),
    private val extractor: EntityExtractor = EntityExtractor(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val scoringVersion: String get() = riskEngine.scoringVersion

    /**
     * @param trustedDomains domains the user marked as trusted; links to them (and their
     * subdomains) are not checked. The message text around them still is.
     * @param reportedNumbers numbers the user reported as scams (any format; normalised here).
     */
    fun analyze(event: SecurityEvent, trustedDomains: Set<String> = emptySet(), reportedNumbers: Set<String> = emptySet()): RiskResult {
        val bounded = if (event.rawInput.length > MAX_INPUT_CHARS) event.copy(rawInput = event.rawInput.take(MAX_INPUT_CHARS)) else event
        val extracted = skipLocalAndTrusted(extract(bounded), trustedDomains)
        val reported = reportedNumbers.mapNotNull { PhoneNumbers.normalize(it) }.toSet()
        val context = DetectionContext(bounded, extracted, reported)

        val outputs = detectors.filter { bounded.type in it.supportedTypes }.map { detector ->
            runCatching { detector.detect(context) }.getOrElse { error ->
                // One broken rule must not sink the whole analysis; record it honestly instead.
                DetectorOutput(checks = listOf(Check(detector.name, CheckStatus.FAILED, error.message)))
            }
        }
        val intelOutput = intel.lookup(bounded, extracted)

        val indicators = (outputs.flatMap { it.indicators } + intelOutput.indicators)
            .distinctBy { Triple(it.type, it.family, it.matchedText) }
        val skipCheck = extracted.skippedUrls.takeIf { it.isNotEmpty() }?.let { skipped ->
            Check("Local & trusted links", CheckStatus.COMPLETED, skipped.joinToString("; ") { "${it.url.displayHost}: ${it.reason}" })
        }
        val checks = outputs.flatMap { it.checks } + intelOutput.checks + listOfNotNull(skipCheck)

        val assessment = riskEngine.assess(bounded.type, indicators, checks)
        val explanation = explainer.explain(bounded.type, assessment, indicators, checks)

        return RiskResult(
            eventId = event.id,
            eventType = event.type,
            riskScore = assessment.score,
            riskLevel = assessment.level,
            category = assessment.category,
            evidenceQuality = assessment.evidenceQuality,
            summary = explanation.summary,
            indicators = indicators.sortedByDescending { it.severity },
            checks = checks,
            recommendation = explanation.recommendation,
            scoringVersion = riskEngine.scoringVersion,
            analyzedAt = clock(),
        )
    }

    private fun skipLocalAndTrusted(extracted: ExtractedEntities, trustedDomains: Set<String>): ExtractedEntities {
        val trusted = trustedDomains.map { it.lowercase().trim().removePrefix("www.") }.filter { it.isNotEmpty() }
        val skipped = mutableListOf<SkippedUrl>()
        val kept = extracted.urls.filter { url ->
            val reason = when {
                DomainUtils.isLocalOrPrivate(url.host) -> "local address, not reachable from the internet"
                trusted.any { DomainUtils.isSameOrSubdomain(url.host, it) } -> "you marked this site as trusted"
                else -> null
            }
            if (reason != null) skipped += SkippedUrl(url, reason)
            reason == null
        }
        return extracted.copy(urls = kept, skippedUrls = skipped)
    }

    private fun extract(event: SecurityEvent): ExtractedEntities = when (event.type) {
        EventType.URL_EVENT, EventType.DOMAIN_EVENT -> ExtractedEntities(urls = listOfNotNull(UrlExtractor.parseSingle(event.rawInput)))
        EventType.SMS_EVENT, EventType.APP_MESSAGE_EVENT, EventType.MANUAL_TEXT_EVENT, EventType.QR_EVENT -> extractor.extractFromText(event.rawInput)
        EventType.CALL_EVENT, EventType.IP_EVENT -> ExtractedEntities.NONE
    }

    companion object {
        const val MAX_INPUT_CHARS = 10_000

        fun defaultDetectors(): List<Detector> = listOf(
            TextRulesDetector(),
            UrlRulesDetector(),
            QrPayloadDetector(),
            NumberFormatDetector(),
            ReportedNumberDetector(),
            AddressDetector(),
        )
    }
}
