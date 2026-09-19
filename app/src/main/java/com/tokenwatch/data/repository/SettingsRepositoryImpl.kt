package com.tokenwatch.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tokenwatch.domain.model.AppSettings
import com.tokenwatch.domain.model.ThemeMode
import com.tokenwatch.domain.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val defaults = AppSettings()

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                themeMode = enumOrNull<ThemeMode>(p[THEME]) ?: defaults.themeMode,
                refreshIntervalMinutes = p[REFRESH_MINUTES] ?: defaults.refreshIntervalMinutes,
                usageAlertsEnabled = p[USAGE_ALERTS] ?: defaults.usageAlertsEnabled,
                sessionAlertPercent = p[SESSION_ALERT] ?: defaults.sessionAlertPercent,
                weeklyAlertPercent = p[WEEKLY_ALERT] ?: defaults.weeklyAlertPercent,
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) = set(THEME, mode.name)

    override suspend fun setRefreshIntervalMinutes(minutes: Int) = set(REFRESH_MINUTES, minutes)

    override suspend fun setUsageAlertsEnabled(enabled: Boolean) = set(USAGE_ALERTS, enabled)

    override suspend fun setSessionAlertPercent(percent: Int) = set(SESSION_ALERT, percent)

    override suspend fun setWeeklyAlertPercent(percent: Int) = set(WEEKLY_ALERT, percent)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        // New key names: the old ones held values from settings that were never wired up.
        val REFRESH_MINUTES = intPreferencesKey("background_refresh_minutes")
        val USAGE_ALERTS = booleanPreferencesKey("usage_limit_alerts")
        val SESSION_ALERT = intPreferencesKey("session_alert_percent")
        val WEEKLY_ALERT = intPreferencesKey("weekly_alert_percent")
    }
}
