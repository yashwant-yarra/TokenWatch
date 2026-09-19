package com.tokenwatch.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.tokenwatch.data.local.AppDatabase
import com.tokenwatch.data.local.SnapshotDao
import com.tokenwatch.data.notify.UsageAlertNotifier
import com.tokenwatch.data.repository.SettingsRepositoryImpl
import com.tokenwatch.data.repository.UsageRepositoryImpl
import com.tokenwatch.domain.repository.SettingsRepository
import com.tokenwatch.domain.repository.UsageAlertSink
import com.tokenwatch.domain.repository.UsageRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ai_usage.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSnapshotDao(database: AppDatabase): SnapshotDao = database.snapshotDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindUsageRepository(impl: UsageRepositoryImpl): UsageRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindUsageAlertSink(impl: UsageAlertNotifier): UsageAlertSink
}
