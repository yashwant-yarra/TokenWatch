package com.tokenwatch.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tokenwatch.core.ui.components.AlertLimits
import com.tokenwatch.core.ui.components.alertLimits
import com.tokenwatch.core.util.clockTicker
import com.tokenwatch.domain.model.QuotaWindow
import com.tokenwatch.domain.repository.SettingsRepository
import com.tokenwatch.domain.repository.UsageRepository
import com.tokenwatch.domain.usecase.HistoryRange
import com.tokenwatch.domain.usecase.WindowGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val range: HistoryRange,
    val group: WindowGroup,
    /** Every provider's stored readings within [range], oldest first. */
    val readings: List<QuotaWindow>,
    val limits: AlertLimits?,
    val now: Instant,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: UsageRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val range = MutableStateFlow(HistoryRange.DAY)
    private val group = MutableStateFlow(WindowGroup.SESSION)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val readings = range.flatMapLatest { r -> repository.historyAll(clock.instant().minus(r.span)).map { r to it } }

    val uiState: StateFlow<HistoryUiState?> = combine(readings, group, settingsRepository.settings, clockTicker(clock)) { (r, rows), g, settings, now ->
        HistoryUiState(r, g, rows, settings.alertLimits(), now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setRange(value: HistoryRange) {
        range.value = value
    }

    fun setGroup(value: WindowGroup) {
        group.value = value
    }
}
