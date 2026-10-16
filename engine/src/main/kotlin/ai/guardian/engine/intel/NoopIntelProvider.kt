package ai.guardian.engine.intel

import ai.guardian.engine.extract.ExtractedEntities
import ai.guardian.engine.model.Check
import ai.guardian.engine.model.CheckStatus
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.SecurityEvent

class NoopIntelProvider : ThreatIntelProvider {
    override fun lookup(event: SecurityEvent, extracted: ExtractedEntities): IntelOutput {
        val checks = buildList {
            if (extracted.urls.isNotEmpty()) add(unavailable("Link reputation"))
            if (extracted.phones.isNotEmpty() || event.type == EventType.CALL_EVENT) add(unavailable("Phone number reputation"))
            if (extracted.upiIds.isNotEmpty()) add(unavailable("UPI ID reputation"))
            if (event.type == EventType.IP_EVENT || event.type == EventType.DOMAIN_EVENT) add(unavailable("Address reputation"))
            if (isEmpty()) add(unavailable("Threat intelligence lookup"))
        }
        return IntelOutput(checks = checks)
    }

    private fun unavailable(name: String) = Check(name, CheckStatus.UNAVAILABLE, NOTE)

    companion object {
        const val NOTE = "External reputation not checked — no threat-intelligence service is connected in this version"
    }
}
