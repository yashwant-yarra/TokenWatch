package com.tokenwatch.domain.repository

import com.tokenwatch.domain.model.AppSettings
import com.tokenwatch.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setRefreshIntervalMinutes(minutes: Int)

    suspend fun setUsageAlertsEnabled(enabled: Boolean)

    suspend fun setSessionAlertPercent(percent: Int)

    suspend fun setWeeklyAlertPercent(percent: Int)

    suspend fun setResetAlertsEnabled(enabled: Boolean)
}
