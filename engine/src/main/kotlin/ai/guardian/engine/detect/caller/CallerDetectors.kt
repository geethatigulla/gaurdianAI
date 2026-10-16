package ai.guardian.engine.detect.caller

import ai.guardian.engine.detect.DetectionContext
import ai.guardian.engine.detect.Detector
import ai.guardian.engine.detect.DetectorOutput
import ai.guardian.engine.model.Check
import ai.guardian.engine.model.CheckStatus
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.Family
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.Severity
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType

/**
 * What can honestly be said about a number without a reputation database: is it real, where is it
 * from, and what kind of line is it. Reputation itself is reported as UNAVAILABLE by the intel layer.
 */
class NumberFormatDetector : Detector {
    override val name = "Phone number format"
    override val supportedTypes = setOf(EventType.CALL_EVENT)

    override fun detect(context: DetectionContext): DetectorOutput {
        val raw = context.event.rawInput.trim()
        if (PhoneNumbers.looksLikeSenderId(raw)) {
            return DetectorOutput(checks = listOf(Check(name, CheckStatus.COMPLETED, "Short code or business sender ID, not a personal number")))
        }
        val info = PhoneNumbers.parse(raw)
            ?: return DetectorOutput(
                indicators = listOf(indicator("UNREADABLE_NUMBER", Severity.MEDIUM, "This doesn't look like a real phone number — caller ID may be faked", raw)),
                checks = listOf(Check(name, CheckStatus.COMPLETED, "Could not read a phone number")),
            )

        val indicators = buildList {
            if (!info.valid) add(indicator("INVALID_NUMBER", Severity.MEDIUM, "Not a valid phone number for its country — caller ID may be faked", info.e164))
            if (info.valid && !info.isDomestic) {
                add(
                    indicator(
                        "INTERNATIONAL_NUMBER", Severity.WEAK,
                        "Number from outside India (${info.countryName ?: info.regionCode}). Indian banks, police and government offices don't call from foreign numbers",
                        info.e164,
                    ),
                )
            }
            when (info.type) {
                PhoneNumberType.PREMIUM_RATE -> add(indicator("PREMIUM_RATE_NUMBER", Severity.MEDIUM, "Premium-rate number — calling or texting it back can cost money", info.e164))
                PhoneNumberType.VOIP -> add(indicator("VOIP_NUMBER", Severity.WEAK, "Internet (VoIP) number — easy to create anonymously", info.e164))
                else -> Unit
            }
        }
        val note = buildString {
            append(if (info.valid) "Valid" else "Invalid")
            append(if (info.isDomestic) " Indian" else " ${info.countryName ?: "international"}")
            append(" ").append(typeLabel(info.type)).append(" number")
            if (info.isDomestic && info.e164.startsWith("+91140")) append(" · registered telemarketing (140) series")
        }
        return DetectorOutput(indicators, listOf(Check(name, CheckStatus.COMPLETED, note)))
    }

    private fun typeLabel(type: PhoneNumberType) = when (type) {
        PhoneNumberType.MOBILE -> "mobile"
        PhoneNumberType.FIXED_LINE -> "landline"
        PhoneNumberType.FIXED_LINE_OR_MOBILE -> "phone"
        PhoneNumberType.TOLL_FREE -> "toll-free"
        PhoneNumberType.PREMIUM_RATE -> "premium-rate"
        PhoneNumberType.VOIP -> "internet (VoIP)"
        else -> "phone"
    }

    private fun indicator(type: String, severity: Severity, description: String, matched: String?) =
        Indicator(type = type, family = Family.NUMBER_FORMAT, severity = severity, description = description, source = "rule:NumberFormatDetector", matchedText = matched)
}

/** Numbers the user reported before. The strongest signal Guardian has without a reputation service. */
class ReportedNumberDetector : Detector {
    override val name = "Your reported numbers"
    override val supportedTypes = setOf(EventType.CALL_EVENT, EventType.SMS_EVENT, EventType.APP_MESSAGE_EVENT, EventType.MANUAL_TEXT_EVENT)

    override fun detect(context: DetectionContext): DetectorOutput {
        if (context.reportedNumbers.isEmpty()) return DetectorOutput.EMPTY
        val event = context.event
        val candidates = buildList {
            if (event.type == EventType.CALL_EVENT) add(event.rawInput)
            event.sender?.let(::add)
            addAll(context.extracted.phones)
        }
        val hit = candidates.firstOrNull { PhoneNumbers.normalize(it) in context.reportedNumbers }
        val indicators = listOfNotNull(
            hit?.let {
                Indicator(
                    type = "REPORTED_NUMBER", family = Family.USER_REPORT, severity = Severity.STRONG,
                    description = "You reported this number as a scam before", source = "rule:ReportedNumberDetector", matchedText = it,
                )
            },
        )
        return DetectorOutput(indicators, listOf(Check(name, CheckStatus.COMPLETED, "${context.reportedNumbers.size} reported number(s) compared")))
    }
}
