package ai.guardian.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ScanEventEntity::class, EvidenceEntity::class, CheckEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class GuardianDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        const val NAME = "guardian.db"
    }
}
