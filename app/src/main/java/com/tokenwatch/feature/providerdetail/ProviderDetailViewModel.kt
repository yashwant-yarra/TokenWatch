package com.tokenwatch.feature.providerdetail

import androidx.lifecycle.SavedStateHandle
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

sealed interface ProviderDetailUiState {
    data object Loading : ProviderDetailUiState

    data class Content(
        val snapshot: ProviderSnapshot,
        val now: Instant,
        val isRefreshing: Boolean,
    ) : ProviderDetailUiState
}

@HiltViewModel
class ProviderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: UsageRepository,
    private val clock: Clock,
) : ViewModel() {

    val provider: AiProvider = AiProvider.valueOf(checkNotNull(savedStateHandle.get<String>(ARG_PROVIDER)))

    val uiState: StateFlow<ProviderDetailUiState> = combine(
        repository.snapshot(provider),
        repository.refreshing,
        clockTicker(clock),
    ) { snapshot, refreshing, now ->
        if (snapshot == null) ProviderDetailUiState.Loading else ProviderDetailUiState.Content(snapshot, now, provider in refreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderDetailUiState.Loading)

    /** Only a signed-in provider can be re-read; pulling on a signed-out one does nothing. */
    fun refresh() {
        if ((uiState.value as? ProviderDetailUiState.Content)?.snapshot?.isActive == true) repository.requestRefresh(provider)
    }

    fun signOut() {
        signOutOfWebSession(provider)
        viewModelScope.launch { repository.disconnect(provider) }
    }

    companion object {
        const val ARG_PROVIDER = "provider"
    }
}
