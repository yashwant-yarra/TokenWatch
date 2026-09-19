package com.tokenwatch.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM provider_snapshots")
    fun observeAll(): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM provider_snapshots WHERE provider = :provider")
    suspend fun get(provider: String): SnapshotEntity?

    @Upsert
    suspend fun upsert(entity: SnapshotEntity)

    @Query("DELETE FROM provider_snapshots")
    suspend fun clear()
}
