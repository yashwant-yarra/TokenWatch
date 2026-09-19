package com.tokenwatch.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One cached snapshot per provider, stored as JSON. The schema stays tiny while the
 * domain model evolves; the backend (Phase 2) owns the normalized relational history.
 */
@Entity(tableName = "provider_snapshots")
data class SnapshotEntity(
    @PrimaryKey val provider: String,
    val json: String,
    val updatedAtMillis: Long,
)
