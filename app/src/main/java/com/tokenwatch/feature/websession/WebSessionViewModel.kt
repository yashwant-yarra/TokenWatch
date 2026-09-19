package com.tokenwatch.feature.websession

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tokenwatch.data.web.UsagePageSpec
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.domain.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebSessionUiState(
    val saving: Boolean = false,
    val message: String? = null,
    val savedAt: Instant? = null,
)

@HiltViewModel
class WebSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: UsageRepository,
    private val clock: Clock,
) : ViewModel() {
    val provider = AiProvider.valueOf(checkNotNull(savedStateHandle.get<String>(ARG_PROVIDER)))
    val spec = UsagePageSpec.forProvider(provider)

    private val _uiState = MutableStateFlow(WebSessionUiState())
    val uiState: StateFlow<WebSessionUiState> = _uiState.asStateFlow()

    /** Automatic reads run every few seconds while the page is open, so only [manual] ones report failures. */
    fun save(rawJson: String, manual: Boolean) {
        if (_uiState.value.saving || _uiState.value.savedAt != null) return
        _uiState.update { it.copy(saving = true, message = if (manual) "Reading usage" else it.message) }
        viewModelScope.launch {
            runCatching {
                repository.saveWebPayload(provider, rawJson)
                clock.instant()
            }.onSuccess { savedAt ->
                _uiState.value = WebSessionUiState(message = "Usage saved", savedAt = savedAt)
            }.onFailure {
                _uiState.update { state ->
                    state.copy(saving = false, message = if (manual) NOT_FOUND else state.message)
                }
            }
        }
    }

    companion object {
        const val ARG_PROVIDER = "provider"
        private const val NOT_FOUND = "No usage values found yet. Open the usage page, wait for it to load, then try again."
    }
}
