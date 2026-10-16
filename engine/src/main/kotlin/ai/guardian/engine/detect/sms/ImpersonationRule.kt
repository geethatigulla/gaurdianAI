package ai.guardian.engine.detect.sms

import ai.guardian.engine.detect.BrandCatalog
import ai.guardian.engine.model.Family
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.Severity

/**
 * Mentioning a bank is normal. Mentioning a bank *and* linking somewhere that isn't that bank's
 * domain is the classic phishing pattern, so that is what we flag.
 */
class ImpersonationRule : TextRule {
    override val id = "ImpersonationRule"

    private val personalNumberSender = Regex("^\\+?\\d{10,13}$")

    override fun evaluate(context: TextContext): List<Indicator> = buildList {
        val mentioned = BrandCatalog.brands.filter { it.mention.containsMatchIn(context.normalized) }

        for (brand in mentioned) {
            val foreignUrl = context.extracted.urls.firstOrNull { url ->
                !brand.isOfficialHost(url.host) && BrandCatalog.officialBrandFor(url.host) == null
            }
            if (foreignUrl != null) {
                add(
                    Indicator(
                        type = "BRAND_LINK_MISMATCH",
                        family = Family.IMPERSONATION,
                        severity = Severity.MEDIUM,
                        description = "Mentions ${brand.name}, but the link goes to ${foreignUrl.registrableDomain}, which is not an official ${brand.name} website",
                        source = "rule:$id",
                        matchedText = foreignUrl.displayHost,
                    ),
                )
                break
            }
        }

        // Banks and government bodies send SMS from registered alphanumeric headers (e.g. "VM-SBIINB"),
        // not from personal 10-digit mobile numbers.
        val sender = context.event.sender?.replace(" ", "")?.replace("-", "")
        val official = mentioned.firstOrNull { it.isBank || it.isGovernment }
        if (sender != null && official != null && personalNumberSender.matches(sender)) {
            add(
                Indicator(
                    type = "PERSONAL_NUMBER_SENDER",
                    family = Family.IMPERSONATION,
                    severity = Severity.MEDIUM,
                    description = "Claims to be from ${official.name} but was sent from a personal mobile number",
                    source = "rule:$id",
                    matchedText = context.event.sender,
                ),
            )
        }

        val authority = BrandCatalog.authorities.firstOrNull { (_, regex) -> regex.containsMatchIn(context.normalized) }
        if (authority != null) {
            add(
                Indicator(
                    type = "AUTHORITY_MENTION",
                    family = Family.IMPERSONATION,
                    severity = Severity.WEAK,
                    description = "Mentions ${authority.first} — scammers often pose as officials to frighten people",
                    source = "rule:$id",
                    matchedText = authority.second.find(context.normalized)?.value,
                ),
            )
        }
    }
}
