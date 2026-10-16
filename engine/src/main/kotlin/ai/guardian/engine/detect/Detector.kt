package ai.guardian.engine.detect

import ai.guardian.engine.extract.ExtractedEntities
import ai.guardian.engine.model.Check
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.SecurityEvent

data class DetectionContext(val event: SecurityEvent, val extracted: ExtractedEntities)

data class DetectorOutput(val indicators: List<Indicator> = emptyList(), val checks: List<Check> = emptyList()) {
    companion object {
        val EMPTY = DetectorOutput()
    }
}

/**
 * A unit of detection. Layer 1 (this build) is rules only; an ML classifier (layer 2) will be
 * another [Detector] and needs no UI changes.
 */
interface Detector {
    val name: String
    val supportedTypes: Set<EventType>
    fun detect(context: DetectionContext): DetectorOutput
}
