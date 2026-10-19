package com.tokenwatch.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tokenwatch.domain.model.AppSettings
import com.tokenwatch.domain.model.ThemeMode
import com.tokenwatch.domain.repository.SettingsRepository
import com.tokenwatch.domain.repository.UsageRepository
import com.tokenwatch.feature.websession.BackgroundRefresh
import com.tokenwatch.feature.websession.ResetWatch
import java.time.Instant
import kotlinx.coroutines.flow.first
import com.tokenwatch.feature.websession.clearAllWebData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val usageRepository: UsageRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    fun setTheme(mode: ThemeMode) = launch { settingsRepository.setThemeMode(mode) }

    fun setRefreshInterval(minutes: Int) = launch {
        settingsRepository.setRefreshIntervalMinutes(minutes)
        BackgroundRefresh.schedule(context, minutes, restart = true)
    }

    fun setUsageAlerts(enabled: Boolean) = launch { settingsRepository.setUsageAlertsEnabled(enabled) }

    fun setSessionAlert(percent: Int) = launch { settingsRepository.setSessionAlertPercent(percent) }

    fun setWeeklyAlert(percent: Int) = launch { settingsRepository.setWeeklyAlertPercent(percent) }

    /** Turning it on queues a read at every upcoming reset straight away; off drops them all. */
    fun setResetAlerts(enabled: Boolean) = launch {
        settingsRepository.setResetAlertsEnabled(enabled)
        if (enabled) {
            val now = Instant.now()
            usageRepository.snapshots.first().filter { it.isActive }.forEach { ResetWatch.schedule(context, it, now) }
        } else {
            ResetWatch.cancelAll(context)
        }
    }

    fun deleteLocalHistory() = launch {
        // Provider sign-ins live only in this app's web view, so this also signs out of all of them.
        clearAllWebData(context)
        usageRepository.clearCache()
        _events.emit(SettingsEvent.Message("Cached usage and web data deleted from this device"))
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
