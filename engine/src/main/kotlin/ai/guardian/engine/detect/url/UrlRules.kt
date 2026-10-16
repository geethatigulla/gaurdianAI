package ai.guardian.engine.detect.url

import ai.guardian.engine.detect.BrandCatalog
import ai.guardian.engine.extract.DomainUtils
import ai.guardian.engine.extract.ParsedUrl
import ai.guardian.engine.model.Family
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.Severity

interface UrlRule {
    val id: String
    fun evaluate(url: ParsedUrl): List<Indicator>
}

private fun UrlRule.indicator(type: String, family: String, severity: Severity, description: String, matched: String?) =
    Indicator(type = type, family = family, severity = severity, description = description, source = "rule:$id", matchedText = matched)

/** Links that point straight at an IP address instead of a name. */
class IpHostRule : UrlRule {
    override val id = "IpHostRule"
    private val encodedIp = Regex("^(0x[0-9a-f]+|\\d{8,10})$")

    override fun evaluate(url: ParsedUrl): List<Indicator> = when {
        url.isIpHost -> listOf(
            indicator("IP_AS_HOST", Family.URL_OBFUSCATION, Severity.MEDIUM, "The link uses a raw IP address instead of a website name", url.displayHost),
        )
        encodedIp.matches(url.host) -> listOf(
            indicator("ENCODED_IP_HOST", Family.URL_OBFUSCATION, Severity.STRONG, "The link hides an IP address in encoded form", url.host),
        )
        else -> emptyList()
    }
}

/** "https://sbi.co.in@evil.xyz" actually opens evil.xyz. */
class UserInfoRule : UrlRule {
    override val id = "UserInfoRule"
    override fun evaluate(url: ParsedUrl): List<Indicator> =
        if (url.userInfo == null) emptyList()
        else listOf(
            indicator(
                "AT_SIGN_IN_URL", Family.URL_OBFUSCATION, Severity.STRONG,
                "The link contains \"@\" — the browser ignores everything before it and actually opens ${url.displayHost}",
                "${url.userInfo}@${url.displayHost}",
            ),
        )
}

class ShortenerRule : UrlRule {
    override val id = "ShortenerRule"
    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly", "rb.gy", "shorturl.at", "tiny.cc",
        "ow.ly", "buff.ly", "s.id", "v.gd", "t.ly", "rebrand.ly", "bl.ink", "short.io", "lnkd.in", "surl.li",
        "qr.ae", "bit.do", "u.to", "clck.ru", "shorturl.com", "tinu.be", "kutt.it", "shrtco.de", "t2m.io",
    )

    override fun evaluate(url: ParsedUrl): List<Indicator> =
        if (url.registrableDomain in shorteners || url.host in shorteners) listOf(
            indicator("URL_SHORTENER", Family.URL_OBFUSCATION, Severity.WEAK, "A link shortener hides where this link really goes", url.displayHost),
        ) else emptyList()
}

/** Internationalised hosts can imitate Latin letters (e.g. Cyrillic "а" in "аmazon"). */
class PunycodeRule : UrlRule {
    override val id = "PunycodeRule"

    override fun evaluate(url: ParsedUrl): List<Indicator> {
        val labels = url.host.split('.')
        val hasNonAscii = url.host.any { it.code > 127 }
        val hasPunycode = labels.any { it.startsWith("xn--") }
        if (!hasNonAscii && !hasPunycode) return emptyList()

        val mixedScript = hasNonAscii && url.host.any { it in 'a'..'z' } &&
            url.host.any { Character.UnicodeScript.of(it.code).let { s -> s == Character.UnicodeScript.CYRILLIC || s == Character.UnicodeScript.GREEK } }
        return if (mixedScript) listOf(
            indicator("MIXED_SCRIPT_HOST", Family.URL_IMPERSONATION, Severity.STRONG, "The web address mixes Latin letters with look-alike letters from another alphabet", url.host),
        ) else listOf(
            indicator("PUNYCODE_HOST", Family.URL_IMPERSONATION, Severity.MEDIUM, "The web address uses special (non-English) characters that can imitate a real site", url.host),
        )
    }
}

/**
 * Near-miss spellings of brand domains: "hdfcbnak.com", "paytrn.in", "g00gle.xyz", "hdfc-bank.com".
 * Uses homoglyph normalisation + optimal-string-alignment edit distance.
 */
class LookalikeDomainRule : UrlRule {
    override val id = "LookalikeDomainRule"

    override fun evaluate(url: ParsedUrl): List<Indicator> {
        if (url.isIpHost || BrandCatalog.officialBrandFor(url.host) != null) return emptyList()
        val label = DomainUtils.registrableLabel(url.host)
        val normalized = normalizeHomoglyphs(label.replace("-", ""))

        for (brand in BrandCatalog.brands) {
            for (token in brand.domainTokens) {
                if (label == token) continue // Exact brand label on a foreign TLD: BrandInDomainRule handles it.
                val threshold = when {
                    token.length >= 9 -> 2
                    token.length >= 6 -> 1
                    else -> 0
                }
                val distance = if (normalized == token) 0 else editDistance(normalized, token)
                if (distance <= threshold) {
                    val official = brand.officialDomains.first { it.contains('.') }
                    return listOf(
                        indicator(
                            "LOOKALIKE_DOMAIN", Family.URL_IMPERSONATION, Severity.STRONG,
                            "\"${url.registrableDomain}\" looks like ${brand.name}'s \"$official\" but is a different website",
                            url.registrableDomain,
                        ),
                    )
                }
            }
        }
        return emptyList()
    }

    companion object {
        private val multiCharGlyphs = listOf("rn" to "m", "vv" to "w", "cl" to "d")
        private val singleCharGlyphs = mapOf('0' to 'o', '1' to 'l', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's')

        fun normalizeHomoglyphs(input: String): String {
            var s = input.map { singleCharGlyphs[it] ?: it }.joinToString("")
            multiCharGlyphs.forEach { (from, to) -> s = s.replace(from, to) }
            return s
        }

        /** Optimal string alignment distance (Damerau–Levenshtein without repeated transpositions). */
        fun editDistance(a: String, b: String): Int {
            val d = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) d[i][0] = i
            for (j in 0..b.length) d[0][j] = j
            for (i in 1..a.length) {
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                        d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                    }
                }
            }
            return d[a.length][b.length]
        }
    }
}

/** A brand name used inside an unrelated address: "sbi-kyc-update.xyz", "paypal.com.secure-login.ru". */
class BrandInDomainRule : UrlRule {
    override val id = "BrandInDomainRule"

    override fun evaluate(url: ParsedUrl): List<Indicator> {
        if (url.isIpHost || BrandCatalog.officialBrandFor(url.host) != null) return emptyList()
        val parts = url.host.split('.', '-').dropLast(1).filter { it.isNotEmpty() }
        for (brand in BrandCatalog.brands) {
            val token = brand.domainTokens.firstOrNull { token -> parts.any { it == token || (token.length >= 6 && it.startsWith(token)) } }
            if (token != null) {
                return listOf(
                    indicator(
                        "BRAND_IN_UNRELATED_DOMAIN", Family.URL_IMPERSONATION, Severity.MEDIUM,
                        "Uses \"$token\" in the address, but ${url.registrableDomain} is not an official ${brand.name} website",
                        url.displayHost,
                    ),
                )
            }
        }
        return emptyList()
    }
}

class SuspiciousTldRule : UrlRule {
    override val id = "SuspiciousTldRule"
    private val tlds = setOf(
        "xyz", "top", "club", "online", "site", "icu", "tk", "ml", "ga", "cf", "gq", "buzz", "rest", "cyou",
        "click", "link", "live", "shop", "vip", "win", "loan", "work", "bid", "support", "help", "fun", "space",
        "website", "pw", "su", "monster", "quest", "sbs", "cfd", "lol", "zip", "mov",
    )

    override fun evaluate(url: ParsedUrl): List<Indicator> =
        if (url.tld in tlds) listOf(
            indicator("SUSPICIOUS_TLD", Family.URL_STRUCTURE, Severity.WEAK, "Uses a \".${url.tld}\" address, a cheap domain ending often used by throwaway scam sites", url.displayHost),
        ) else emptyList()
}

class UrlShapeRule : UrlRule {
    override val id = "UrlShapeRule"

    override fun evaluate(url: ParsedUrl): List<Indicator> = buildList {
        if (url.original.length > 120) {
            add(indicator("LONG_URL", Family.URL_STRUCTURE, Severity.WEAK, "The link is unusually long, which can hide its real destination", "${url.original.length} characters"))
        }
        if (url.subdomainLabels.size >= 3) {
            add(indicator("MANY_SUBDOMAINS", Family.URL_STRUCTURE, Severity.WEAK, "The address has many sub-parts, a trick to push the real site name out of view", url.displayHost))
        }
        if (url.port != null && url.port != 80 && url.port != 443) {
            add(indicator("UNUSUAL_PORT", Family.URL_STRUCTURE, Severity.WEAK, "The link uses an unusual network port (${url.port})", ":${url.port}"))
        }
    }
}

class LureKeywordRule : UrlRule {
    override val id = "LureKeywordRule"
    private val keywords = Regex("(login|signin|sign-in|verify|verification|kyc|update|secure|account|banking|wallet|reward|claim|refund|unlock|suspend|blocked|bonus|free-?gift|otp)")

    override fun evaluate(url: ParsedUrl): List<Indicator> {
        if (BrandCatalog.officialBrandFor(url.host) != null) return emptyList()
        val haystack = (url.subdomainLabels.joinToString(".") + " " + DomainUtils.registrableLabel(url.host) + " " + url.path).lowercase()
        val match = keywords.find(haystack) ?: return emptyList()
        return listOf(indicator("LURE_KEYWORDS_IN_URL", Family.URL_STRUCTURE, Severity.WEAK, "The address contains words like \"${match.value}\" that phishing pages use to look official", match.value))
    }
}

class InsecureTransportRule : UrlRule {
    override val id = "InsecureTransportRule"
    override fun evaluate(url: ParsedUrl): List<Indicator> =
        if (url.scheme == "http") listOf(
            indicator("NO_HTTPS", Family.URL_TRANSPORT, Severity.WEAK, "The link does not use a secure (https) connection", "http://"),
        ) else emptyList()
}

class ApkLinkRule : UrlRule {
    override val id = "ApkLinkRule"
    override fun evaluate(url: ParsedUrl): List<Indicator> {
        val path = url.path.lowercase()
        return if (path.endsWith(".apk") || path.endsWith(".xapk") || path.endsWith(".apks")) listOf(
            indicator(
                "APK_DOWNLOAD_LINK", Family.APK_LURE, Severity.STRONG,
                "The link downloads an Android app file (.apk) from outside the Play Store",
                url.path.substringAfterLast('/'),
            ),
        ) else emptyList()
    }
}
