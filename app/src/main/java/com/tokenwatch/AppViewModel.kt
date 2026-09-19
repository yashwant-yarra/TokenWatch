package com.tokenwatch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tokenwatch.domain.model.AppSettings
import com.tokenwatch.domain.repository.SettingsRepository
import com.tokenwatch.domain.repository.UsageRepository
import com.tokenwatch.feature.websession.BackgroundRefresh
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Activity-scoped: runs the startup refresh once and keeps background refresh matching Settings. */
@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext context: Context,
    settingsRepository: SettingsRepository,
    usageRepository: UsageRepository,
) : ViewModel() {

    /** Null until the saved settings load, so the first frame never uses the wrong theme. */
    val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            if (usageRepository.snapshots.first().isEmpty()) usageRepository.refreshAll()
        }
        viewModelScope.launch {
            // Restores the chain if it was lost (for example after a force stop); keeps it otherwise.
            BackgroundRefresh.schedule(context, settingsRepository.settings.first().refreshIntervalMinutes, restart = false)
        }
    }
}
