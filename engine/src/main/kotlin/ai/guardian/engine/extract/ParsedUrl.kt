package ai.guardian.engine.extract

import java.util.Locale

/**
 * A leniently parsed web address. We deliberately do not use java.net.URI: it rejects many of the
 * malformed-but-clickable links scammers send, which is exactly the input we need to inspect.
 */
data class ParsedUrl(
    /** The text as found (after trimming trailing punctuation). */
    val original: String,
    /** Lowercased scheme, or null for bare links like "sbi-kyc.xyz/login". */
    val scheme: String?,
    val userInfo: String?,
    /** Lowercased host with no trailing dot. IPv6 hosts are stored without brackets. */
    val host: String,
    val port: Int?,
    val path: String,
    val query: String?,
    val fragment: String?,
) {
    val isIpv4Host: Boolean get() = DomainUtils.isIpv4(host)
    val isIpv6Host: Boolean get() = DomainUtils.isIpv6(host)
    val isIpHost: Boolean get() = isIpv4Host || isIpv6Host

    /** e.g. "login.sbi-kyc.co.in" → "sbi-kyc.co.in". For IP hosts returns the host. */
    val registrableDomain: String get() = DomainUtils.registrableDomain(host)

    /** Top-level label, e.g. "xyz". Empty for IP hosts. */
    val tld: String get() = if (isIpHost) "" else host.substringAfterLast('.')

    /** Labels to the left of the registrable domain, e.g. ["secure", "login"]. */
    val subdomainLabels: List<String>
        get() {
            if (isIpHost) return emptyList()
            val reg = registrableDomain
            if (host == reg) return emptyList()
            return host.removeSuffix(".$reg").split('.').filter { it.isNotEmpty() }
        }

    /** Host for display (brackets restored for IPv6). */
    val displayHost: String get() = if (isIpv6Host) "[$host]" else host
}

object UrlParser {
    private val schemeRegex = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://")

    fun parse(input: String): ParsedUrl? {
        val original = input.trim()
        if (original.isEmpty() || original.any { it.isWhitespace() }) return null

        val schemeMatch = schemeRegex.find(original)
        val scheme = schemeMatch?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        val rest = if (schemeMatch != null) original.substring(schemeMatch.range.last + 1) else original

        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        val remainder = rest.substring(authorityEnd)

        val atIndex = authority.lastIndexOf('@')
        val userInfo = if (atIndex >= 0) authority.substring(0, atIndex) else null
        val hostPort = if (atIndex >= 0) authority.substring(atIndex + 1) else authority

        val host: String
        val portText: String?
        if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close < 0) return null
            host = hostPort.substring(1, close)
            portText = hostPort.substring(close + 1).removePrefix(":").ifEmpty { null }
        } else {
            val colon = hostPort.lastIndexOf(':')
            if (colon >= 0) {
                host = hostPort.substring(0, colon)
                portText = hostPort.substring(colon + 1).ifEmpty { null }
            } else {
                host = hostPort
                portText = null
            }
        }
        val normalizedHost = host.lowercase(Locale.ROOT).trimEnd('.')
        if (normalizedHost.isEmpty()) return null
        val port = portText?.toIntOrNull()
        if (portText != null && (port == null || port !in 1..65535)) return null

        val fragmentIndex = remainder.indexOf('#')
        val beforeFragment = if (fragmentIndex >= 0) remainder.substring(0, fragmentIndex) else remainder
        val fragment = if (fragmentIndex >= 0) remainder.substring(fragmentIndex + 1) else null
        val queryIndex = beforeFragment.indexOf('?')
        val path = if (queryIndex >= 0) beforeFragment.substring(0, queryIndex) else beforeFragment
        val query = if (queryIndex >= 0) beforeFragment.substring(queryIndex + 1) else null

        return ParsedUrl(
            original = original,
            scheme = scheme,
            userInfo = userInfo?.ifEmpty { null },
            host = normalizedHost,
            port = port,
            path = path,
            query = query,
            fragment = fragment,
        )
    }
}

object DomainUtils {
    /**
     * Multi-label public suffixes we know about. This is a small, hand-maintained subset of the
     * Public Suffix List focused on Indian users plus common free hosting platforms (so that
     * "sbi-login.web.app" is treated as its own site, not as "web.app").
     */
    private val multiLabelSuffixes = setOf(
        "co.in", "org.in", "net.in", "gov.in", "ac.in", "edu.in", "nic.in", "res.in", "firm.in",
        "gen.in", "ind.in", "bank.in", "co.uk", "org.uk", "gov.uk", "ac.uk", "com.au", "net.au",
        "org.au", "co.nz", "com.sg", "com.br", "co.jp", "com.cn", "com.hk", "co.za", "com.pk",
        "com.bd", "com.np", "github.io", "blogspot.com", "web.app", "firebaseapp.com",
        "netlify.app", "vercel.app", "pages.dev", "herokuapp.com", "glitch.me", "000webhostapp.com",
        "weebly.com", "wixsite.com", "s3.amazonaws.com", "azurewebsites.net", "appspot.com",
        "workers.dev", "ngrok.io", "ngrok-free.app", "repl.co", "godaddysites.com",
    )

    private val ipv4Regex = Regex("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$")
    private val ipv6Regex = Regex("^[0-9a-f:.]+$")

    fun isIpv4(host: String): Boolean = ipv4Regex.matches(host)

    fun isIpv6(host: String): Boolean = host.count { it == ':' } >= 2 && ipv6Regex.matches(host)

    fun registrableDomain(host: String): String {
        if (isIpv4(host) || isIpv6(host)) return host
        val labels = host.split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        // Longest known suffix wins (e.g. "s3.amazonaws.com" before "amazonaws.com").
        for (suffixLabels in 3 downTo 2) {
            if (labels.size > suffixLabels) {
                val candidate = labels.takeLast(suffixLabels).joinToString(".")
                if (candidate in multiLabelSuffixes) return labels.takeLast(suffixLabels + 1).joinToString(".")
            }
        }
        return labels.takeLast(2).joinToString(".")
    }

    /** The registrable domain without its public suffix, e.g. "hdfcbank.com" → "hdfcbank". */
    fun registrableLabel(host: String): String {
        val reg = registrableDomain(host)
        if (isIpv4(reg) || isIpv6(reg)) return reg
        return reg.substringBefore('.')
    }

    private val localSuffixes = listOf(".localhost", ".local", ".test", ".internal", ".lan", ".home.arpa", ".example", ".invalid")

    /**
     * Addresses that only exist on your own device or network (localhost, 192.168.x.x, *.local…).
     * A scammer can't send you a working link to one, so link rules don't apply to them.
     */
    fun isLocalOrPrivate(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h == "localhost" || localSuffixes.any { h.endsWith(it) }) return true
        if (isIpv4(h)) {
            val (a, b) = h.split('.').map { it.toInt() }
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
                (a == 169 && b == 254) || (a == 100 && b in 64..127) || a == 0
        }
        if (isIpv6(h)) return h == "::1" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")
        return false
    }

    /** True if [host] equals [domain] or is a subdomain of it. */
    fun isSameOrSubdomain(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")
}
