package ai.guardian.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ScanEventEntity)

    @Insert
    suspend fun insertEvidence(evidence: List<EvidenceEntity>)

    @Insert
    suspend fun insertChecks(checks: List<CheckEntity>)

    @Transaction
    suspend fun insertScan(event: ScanEventEntity, evidence: List<EvidenceEntity>, checks: List<CheckEntity>) {
        insertEvent(event)
        insertEvidence(evidence)
        insertChecks(checks)
    }

    @Transaction
    @Query("SELECT * FROM scan_events WHERE id = :id")
    suspend fun getScan(id: String): ScanWithDetails?

    @Query("SELECT * FROM scan_events ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ScanEventEntity>>

    @Query("SELECT * FROM scan_events ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ScanEventEntity>>

    @Query("SELECT COUNT(*) FROM scan_events WHERE created_at >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM scan_events WHERE created_at >= :since AND risk_score >= :minScore")
    fun observeCountAtLeast(minScore: Int, since: Long): Flow<Int>

    @Query("UPDATE scan_events SET user_feedback = :feedback WHERE id = :id")
    suspend fun setFeedback(id: String, feedback: String?)

    @Query("DELETE FROM scan_events WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM scan_events")
    suspend fun deleteAll()

    @Query("DELETE FROM scan_events WHERE created_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}
