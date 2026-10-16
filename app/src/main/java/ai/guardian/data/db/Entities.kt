package ai.guardian.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "scan_events",
    indices = [Index("created_at"), Index("event_type"), Index("risk_level")],
)
data class ScanEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "event_type") val eventType: String,
    val source: String,
    @ColumnInfo(name = "risk_score") val riskScore: Int,
    @ColumnInfo(name = "risk_level") val riskLevel: String,
    val category: String,
    @ColumnInfo(name = "evidence_quality") val evidenceQuality: String,
    @ColumnInfo(name = "scoring_version") val scoringVersion: String,
    val summary: String,
    val recommendation: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Short, non-sensitive label for lists, e.g. "Link · sbi-kyc.xyz" or "Message · 142 chars". */
    @ColumnInfo(name = "display_label") val displayLabel: String,
    /** Only set when the user opted in to storing text; always passed through the Redactor. */
    @ColumnInfo(name = "raw_input_redacted") val rawInputRedacted: String?,
    @ColumnInfo(name = "user_feedback") val userFeedback: String?,
)

@Entity(
    tableName = "scan_evidence",
    foreignKeys = [ForeignKey(entity = ScanEventEntity::class, parentColumns = ["id"], childColumns = ["scan_event_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("scan_event_id")],
)
data class EvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "scan_event_id") val scanEventId: String,
    @ColumnInfo(name = "indicator_type") val indicatorType: String,
    val family: String,
    val severity: String,
    val description: String,
    val source: String,
    @ColumnInfo(name = "matched_text") val matchedText: String?,
    val position: Int,
)

@Entity(
    tableName = "scan_checks",
    foreignKeys = [ForeignKey(entity = ScanEventEntity::class, parentColumns = ["id"], childColumns = ["scan_event_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("scan_event_id")],
)
data class CheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "scan_event_id") val scanEventId: String,
    val name: String,
    val status: String,
    val note: String?,
    val position: Int,
)

data class ScanWithDetails(
    @Embedded val event: ScanEventEntity,
    @Relation(parentColumn = "id", entityColumn = "scan_event_id") val evidence: List<EvidenceEntity>,
    @Relation(parentColumn = "id", entityColumn = "scan_event_id") val checks: List<CheckEntity>,
)
