package ai.guardian.engine.detect.sms

import ai.guardian.engine.detect.DetectionContext
import ai.guardian.engine.detect.Detector
import ai.guardian.engine.detect.DetectorOutput
import ai.guardian.engine.model.Check
import ai.guardian.engine.model.CheckStatus
import ai.guardian.engine.model.EventType

class TextRulesDetector(private val rules: List<TextRule> = defaultRules()) : Detector {
    override val name = "Message content rules"
    override val supportedTypes = setOf(EventType.SMS_EVENT, EventType.APP_MESSAGE_EVENT, EventType.MANUAL_TEXT_EVENT, EventType.QR_EVENT)

    override fun detect(context: DetectionContext): DetectorOutput {
        val text = context.event.rawInput
        if (text.isBlank()) {
            return DetectorOutput(checks = listOf(Check(name, CheckStatus.SKIPPED, "Message was empty")))
        }
        val textContext = TextContext(context.event, text, TextNormalizer.normalize(text), context.extracted)
        val indicators = rules.flatMap { it.evaluate(textContext) }
        return DetectorOutput(
            indicators = indicators,
            checks = listOf(Check(name, CheckStatus.COMPLETED, "${rules.size} on-device rules checked")),
        )
    }

    companion object {
        fun defaultRules(): List<TextRule> = listOf(
            UrgencyRule(),
            AccountThreatRule(),
            AuthorityThreatRule(),
            CredentialRequestRule(),
            ImpersonationRule(),
            PaymentLureRule(),
            RewardLureRule(),
            JobLureRule(),
            ApkLureRule(),
            RemoteAccessRule(),
        )
    }
}
