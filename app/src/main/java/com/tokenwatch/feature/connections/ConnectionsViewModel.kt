package com.tokenwatch.feature.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tokenwatch.core.util.clockTicker
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.model.ProviderSnapshot
import com.tokenwatch.domain.repository.UsageRepository
import com.tokenwatch.feature.websession.signOutOfWebSession
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ConnectionsUiState {
    data object Loading : ConnectionsUiState

    data class Content(
        val connections: List<ProviderSnapshot>,
        val now: Instant,
    ) : ConnectionsUiState
}

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val repository: UsageRepository,
    clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<ConnectionsUiState> = combine(
        repository.snapshots,
        clockTicker(clock),
    ) { snapshots, now ->
        if (snapshots.isEmpty()) ConnectionsUiState.Loading else ConnectionsUiState.Content(snapshots, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionsUiState.Loading)

    fun signOut(provider: AiProvider) {
        signOutOfWebSession(provider)
        viewModelScope.launch { repository.disconnect(provider) }
    }
}
