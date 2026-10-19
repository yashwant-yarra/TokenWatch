package com.tokenwatch.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One quota window as it was read at one moment: the rows behind the usage history chart. */
@Entity(tableName = "usage_readings", indices = [Index(value = ["provider", "observedAtMillis"])])
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    val windowId: String,
    val label: String,
    val windowType: String,
    val usedPercent: Double?,
    val resetsAtMillis: Long?,
    val observedAtMillis: Long,
)

@Dao
interface ReadingDao {
    @Insert
    suspend fun insertAll(readings: List<ReadingEntity>)

    @Query("SELECT * FROM usage_readings WHERE provider = :provider AND observedAtMillis >= :sinceMillis ORDER BY observedAtMillis")
    fun observeSince(provider: String, sinceMillis: Long): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM usage_readings WHERE observedAtMillis >= :sinceMillis ORDER BY observedAtMillis")
    fun observeAllSince(sinceMillis: Long): Flow<List<ReadingEntity>>

    @Query("DELETE FROM usage_readings WHERE observedAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM usage_readings WHERE provider = :provider")
    suspend fun deleteProvider(provider: String)

    @Query("DELETE FROM usage_readings")
    suspend fun clear()
}

/** Adds the history table without touching the cached snapshots (version 1 had only those). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `usage_readings` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `provider` TEXT NOT NULL, `windowId` TEXT NOT NULL, " +
                "`label` TEXT NOT NULL, `windowType` TEXT NOT NULL, `usedPercent` REAL, `resetsAtMillis` INTEGER, " +
                "`observedAtMillis` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_usage_readings_provider_observedAtMillis` ON `usage_readings` (`provider`, `observedAtMillis`)",
        )
    }
}
