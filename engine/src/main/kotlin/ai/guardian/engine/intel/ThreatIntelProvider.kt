package ai.guardian.engine.intel

import ai.guardian.engine.extract.ExtractedEntities
import ai.guardian.engine.model.Check
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.SecurityEvent

data class IntelOutput(val indicators: List<Indicator> = emptyList(), val checks: List<Check> = emptyList())

/**
 * Layer 3: external reputation (URL blocklists, number reputation, UPI fraud lists).
 * v0.1 ships [NoopIntelProvider], which reports every lookup as UNAVAILABLE rather than pretending
 * to know. A backend-backed implementation slots in here without UI changes.
 */
interface ThreatIntelProvider {
    fun lookup(event: SecurityEvent, extracted: ExtractedEntities): IntelOutput
}
