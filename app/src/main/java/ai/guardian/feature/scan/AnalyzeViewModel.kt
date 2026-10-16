package ai.guardian.feature.scan

import ai.guardian.data.repo.AnalyzeScanUseCase
import ai.guardian.engine.extract.DomainUtils
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.navigation.Routes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class InputMode(val title: String, val placeholder: String, val singleLine: Boolean) {
    TEXT("Check a message", "Paste a message", singleLine = false),
    URL("Check a link", "Paste a link", singleLine = true),
    PHONE("Check a number", "e.g. +91 98765 43210", singleLine = true),
    ADDRESS("Check IP / domain", "e.g. 192.168.1.1 or example.com", singleLine = true);

    fun eventTypeFor(input: String): EventType = when (this) {
        TEXT -> EventType.MANUAL_TEXT_EVENT
        URL -> EventType.URL_EVENT
        PHONE -> EventType.CALL_EVENT
        ADDRESS -> {
            val host = input.trim().removePrefix("[").removeSuffix("]")
            if (DomainUtils.isIpv4(host) || DomainUtils.isIpv6(host.lowercase())) EventType.IP_EVENT else EventType.DOMAIN_EVENT
        }
    }
}

data class AnalyzeUiState(
    val mode: InputMode,
    val input: String = "",
    val analyzing: Boolean = false,
    val error: String? = null,
) {
    val canAnalyze: Boolean get() = input.isNotBlank() && !analyzing && input.length <= MAX_INPUT
    val tooLong: Boolean get() = input.length > MAX_INPUT

    companion object {
        const val MAX_INPUT = 5_000
    }
}

@HiltViewModel
class AnalyzeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val analyze: AnalyzeScanUseCase,
) : ViewModel() {

    private val mode = savedStateHandle.get<String>(Routes.ARG_MODE)?.let { runCatching { InputMode.valueOf(it) }.getOrNull() } ?: InputMode.TEXT

    private val _state = MutableStateFlow(AnalyzeUiState(mode))
    val state: StateFlow<AnalyzeUiState> = _state.asStateFlow()

    private val _results = Channel<String>(Channel.BUFFERED)
    /** One-shot navigation events carrying the new scan id. */
    val results = _results.receiveAsFlow()

    fun onInputChange(value: String) = _state.update { it.copy(input = value, error = null) }

    fun analyze() {
        val current = _state.value
        if (!current.canAnalyze) return
        _state.update { it.copy(analyzing = true, error = null) }
        viewModelScope.launch {
            runCatching { analyze(current.mode.eventTypeFor(current.input), current.input.trim(), EventSource.MANUAL) }
                .onSuccess { record ->
                    _state.update { it.copy(analyzing = false) }
                    _results.send(record.result.eventId)
                }
                .onFailure { e -> _state.update { it.copy(analyzing = false, error = "Couldn't analyse this: ${e.message ?: "unknown error"}") } }
        }
    }
}
