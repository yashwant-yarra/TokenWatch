package com.tokenwatch.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SnapshotEntity::class, ReadingEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun snapshotDao(): SnapshotDao

    abstract fun readingDao(): ReadingDao
}
