package com.tokenwatch.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tokenwatch.core.ui.components.AlertLimits
import com.tokenwatch.core.ui.components.alertLimits
import com.tokenwatch.core.ui.components.lowestLeft
import com.tokenwatch.core.ui.components.message
import com.tokenwatch.core.util.clockTicker
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ConnectionStatus
import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.repository.SettingsRepository
import com.tokenwatch.domain.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data object Empty : DashboardUiState

    data class Error(val message: String, val isRefreshing: Boolean) : DashboardUiState

    data class Content(
        val providers: List<ProviderSnapshot>,
        val lastUpdated: Instant?,
        val isStale: Boolean,
        val isRefreshing: Boolean,
        /** Providers being re-read right now; their glasses spin. */
        val refreshing: Set<AiProvider>,
        val now: Instant,
        /** The user's alert lines, drawn on every glass; null when alerts are off. */
        val limits: AlertLimits?,
    ) : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: UsageRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.snapshots,
        repository.refreshing,
        repository.hasRefreshed,
        clockTicker(clock),
        settingsRepository.settings,
    ) { snapshots, refreshing, hasRefreshed, now, settings ->
        build(snapshots, refreshing, hasRefreshed, now, settings.alertLimits())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.Loading)

    /** Pull to refresh: re-reads every provider that has a sign-in. */
    fun refreshAll() {
        (uiState.value as? DashboardUiState.Content)?.providers
            ?.map { it.connection }
            ?.filter { it.status != ConnectionStatus.DISCONNECTED }
            ?.forEach { repository.requestRefresh(it.provider) }
    }

    private fun build(
        snapshots: List<ProviderSnapshot>,
        refreshing: Set<AiProvider>,
        hasRefreshed: Boolean,
        now: Instant,
        limits: AlertLimits?,
    ): DashboardUiState {
        if (snapshots.isEmpty()) return DashboardUiState.Loading
        val isRefreshing = refreshing.isNotEmpty()
        val active = snapshots.filter { it.isActive }
        if (active.isEmpty()) {
            return if (isRefreshing && !hasRefreshed) DashboardUiState.Loading else DashboardUiState.Empty
        }
        val allFailedWithoutData = active.all { !it.hasData && it.lastFailure != null }
        if (allFailedWithoutData && !isRefreshing) {
            val first = active.first()
            val message = first.lastFailure?.message(first.provider, now) ?: "Usage couldn't be loaded."
            return DashboardUiState.Error(message, isRefreshing)
        }

        val lastUpdated = active.mapNotNull { it.fetchedAt }.minOrNull()
        val isStale = active.any { it.lastFailure != null } ||
            (lastUpdated != null && Duration.between(lastUpdated, now) > STALE_AFTER)

        return DashboardUiState.Content(
            // Danger first: the provider with the least left leads; unread and disconnected ones follow.
            providers = snapshots.sortedWith(
                compareBy<ProviderSnapshot> { it.connection.status == ConnectionStatus.DISCONNECTED }
                    .thenBy { it.lowestLeft ?: Double.MAX_VALUE },
            ),
            lastUpdated = lastUpdated,
            isStale = isStale,
            isRefreshing = isRefreshing,
            refreshing = refreshing,
            now = now,
            limits = limits,
        )
    }

    private companion object {
        val STALE_AFTER: Duration = Duration.ofHours(1)
    }
}
