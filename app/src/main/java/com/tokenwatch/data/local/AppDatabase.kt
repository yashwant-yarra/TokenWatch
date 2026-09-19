package com.tokenwatch.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SnapshotEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun snapshotDao(): SnapshotDao
}
