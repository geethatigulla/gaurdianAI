package ai.guardian.navigation

import ai.guardian.data.repo.AnalyzeScanUseCase
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.feature.scan.InputMode
import ai.guardian.platform.incoming.IncomingRequest
import ai.guardian.platform.incoming.IncomingRequests
import ai.guardian.platform.qr.QrDecoder
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface IncomingNav {
    data class Result(val id: String) : IncomingNav
    data class Input(val mode: InputMode) : IncomingNav
    data object QrScanner : IncomingNav
}

data class IncomingUiState(val checking: Boolean = false, val error: String? = null)

/** Runs whatever MainActivity received (share, link, widget tap) and tells the UI where to go. */
@HiltViewModel
class IncomingViewModel @Inject constructor(
    private val requests: IncomingRequests,
    private val analyze: AnalyzeScanUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(IncomingUiState())
    val state: StateFlow<IncomingUiState> = _state.asStateFlow()

    private val _nav = Channel<IncomingNav>(Channel.BUFFERED)
    val nav = _nav.receiveAsFlow()

    init {
        viewModelScope.launch {
            requests.pending.filterNotNull().collect { request ->
                requests.consume(request)
                handle(request)
            }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    private suspend fun handle(request: IncomingRequest) {
        when (request) {
            is IncomingRequest.OpenResult -> _nav.send(IncomingNav.Result(request.id))
            is IncomingRequest.OpenInput -> _nav.send(IncomingNav.Input(request.mode))
            IncomingRequest.OpenQrScanner -> _nav.send(IncomingNav.QrScanner)
            is IncomingRequest.Error -> _state.value = IncomingUiState(error = request.message)
            is IncomingRequest.Analyze -> run(request.type, request.input, request.source)
            is IncomingRequest.DecodeQrImage -> {
                _state.value = IncomingUiState(checking = true)
                val payload = withContext(Dispatchers.IO) { QrDecoder.decode(context, request.uri) }
                if (payload == null) {
                    _state.value = IncomingUiState(error = "No QR code was found in that image.")
                } else {
                    run(EventType.QR_EVENT, payload, EventSource.QR_IMAGE)
                }
            }
        }
    }

    private suspend fun run(type: EventType, input: String, source: EventSource) {
        _state.value = IncomingUiState(checking = true)
        runCatching { analyze(type, input, source) }
            .onSuccess {
                _state.value = IncomingUiState()
                _nav.send(IncomingNav.Result(it.result.eventId))
            }
            .onFailure { _state.value = IncomingUiState(error = "Couldn't check this: ${it.message ?: "unknown error"}") }
    }
}
