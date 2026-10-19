package com.tokenwatch.feature.providerdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tokenwatch.core.ui.components.AlertLimits
import com.tokenwatch.core.ui.components.alertLimits
import com.tokenwatch.core.util.clockTicker
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.repository.SettingsRepository
import com.tokenwatch.domain.repository.UsageRepository
import com.tokenwatch.feature.websession.signOutOfWebSession
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import com.tokenwatch.domain.model.QuotaWindow
import com.tokenwatch.domain.usecase.HistoryRange
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProviderDetailUiState {
    data object Loading : ProviderDetailUiState

    data class Content(
        val snapshot: ProviderSnapshot,
        val now: Instant,
        val isRefreshing: Boolean,
        val limits: AlertLimits?,
        val historyRange: HistoryRange,
        /** Stored readings of every window within [historyRange], oldest first. */
        val history: List<QuotaWindow>,
    ) : ProviderDetailUiState
}

@HiltViewModel
class ProviderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: UsageRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    val provider: AiProvider = AiProvider.valueOf(checkNotNull(savedStateHandle.get<String>(ARG_PROVIDER)))

    private val historyRange = MutableStateFlow(HistoryRange.DAY)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val history = historyRange.flatMapLatest { range ->
        repository.history(provider, clock.instant().minus(range.span)).map { range to it }
    }

    val uiState: StateFlow<ProviderDetailUiState> = combine(
        repository.snapshot(provider),
        repository.refreshing,
        clockTicker(clock),
        settingsRepository.settings,
        history,
    ) { snapshot, refreshing, now, settings, (range, readings) ->
        if (snapshot == null) {
            ProviderDetailUiState.Loading
        } else {
            ProviderDetailUiState.Content(snapshot, now, provider in refreshing, settings.alertLimits(), range, readings)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderDetailUiState.Loading)

    /** Only a signed-in provider can be re-read; pulling on a signed-out one does nothing. */
    fun refresh() {
        if ((uiState.value as? ProviderDetailUiState.Content)?.snapshot?.isActive == true) repository.requestRefresh(provider)
    }

    fun setHistoryRange(range: HistoryRange) {
        historyRange.value = range
    }

    fun signOut() {
        signOutOfWebSession(provider)
        viewModelScope.launch { repository.disconnect(provider) }
    }

    companion object {
        const val ARG_PROVIDER = "provider"
    }
}
