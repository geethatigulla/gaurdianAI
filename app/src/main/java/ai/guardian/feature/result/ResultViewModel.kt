package ai.guardian.feature.result

import ai.guardian.data.repo.ScanRecord
import ai.guardian.data.repo.ScanRepository
import ai.guardian.data.repo.SettingsRepository
import ai.guardian.data.repo.UserFeedback
import ai.guardian.navigation.Routes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ResultUiState {
    data object Loading : ResultUiState
    data object NotFound : ResultUiState
    data class Loaded(val record: ScanRecord) : ResultUiState
    data object Deleted : ResultUiState
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ScanRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val id: String = checkNotNull(savedStateHandle[Routes.ARG_ID])

    private val _state = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = repository.get(id)?.let { ResultUiState.Loaded(it) } ?: ResultUiState.NotFound
        }
    }

    fun setFeedback(feedback: UserFeedback) {
        val loaded = _state.value as? ResultUiState.Loaded ?: return
        val next = if (loaded.record.userFeedback == feedback) null else feedback
        viewModelScope.launch {
            repository.setFeedback(id, next)
            _state.value = ResultUiState.Loaded(loaded.record.copy(userFeedback = next))
        }
    }

    /** Future link checks skip this domain; already-saved results are not changed. */
    fun trustDomain(domain: String) {
        viewModelScope.launch { settings.trustDomain(domain) }
    }

    /** Future calls/SMS from this number are flagged (and blocked, if the user enabled that). */
    fun reportNumber(number: String) {
        viewModelScope.launch { settings.reportNumber(number) }
    }

    fun delete() {
        viewModelScope.launch {
            repository.delete(id)
            _state.value = ResultUiState.Deleted
        }
    }
}
