package ai.guardian.engine.detect.address

import ai.guardian.engine.detect.DetectionContext
import ai.guardian.engine.detect.Detector
import ai.guardian.engine.detect.DetectorOutput
import ai.guardian.engine.extract.DomainUtils
import ai.guardian.engine.model.Check
import ai.guardian.engine.model.CheckStatus
import ai.guardian.engine.model.EventType

/**
 * Classifies an IP address. Without DNS/ASN/reputation data (unavailable in v0.1) there is little
 * an IP alone proves, so this reports what it is rather than inventing a risk.
 */
class AddressDetector : Detector {
    override val name = "IP address type"
    override val supportedTypes = setOf(EventType.IP_EVENT)

    private val documentationRanges = listOf("192.0.2.", "198.51.100.", "203.0.113.")

    override fun detect(context: DetectionContext): DetectorOutput {
        val host = context.event.rawInput.trim().removePrefix("[").removeSuffix("]").lowercase()
        val note = when {
            !DomainUtils.isIpv4(host) && !DomainUtils.isIpv6(host) ->
                return DetectorOutput(checks = listOf(Check(name, CheckStatus.FAILED, "Not a valid IPv4 or IPv6 address")))
            host == "127.0.0.1" || host == "::1" -> "Loopback address: this device itself"
            DomainUtils.isLocalOrPrivate(host) -> "Private address: only reachable inside your own network"
            documentationRanges.any { host.startsWith(it) } -> "Documentation range: not used on the real internet"
            DomainUtils.isIpv6(host) -> "Public IPv6 internet address"
            else -> "Public IPv4 internet address"
        }
        return DetectorOutput(checks = listOf(Check(name, CheckStatus.COMPLETED, note)))
    }
}
