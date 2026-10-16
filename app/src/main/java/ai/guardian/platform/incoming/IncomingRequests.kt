package ai.guardian.platform.incoming

import ai.guardian.engine.extract.UrlExtractor
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.feature.scan.InputMode
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Something the outside world asked Guardian to do: shared text, a tapped link, a widget button… */
sealed interface IncomingRequest {
    data class Analyze(val type: EventType, val input: String, val source: EventSource) : IncomingRequest
    data class DecodeQrImage(val uri: Uri) : IncomingRequest
    data class OpenResult(val id: String) : IncomingRequest
    data class OpenInput(val mode: InputMode) : IncomingRequest
    data object OpenQrScanner : IncomingRequest
    data class Error(val message: String) : IncomingRequest
}

/** Hand-off between MainActivity (which receives intents) and the UI (which acts on them). */
@Singleton
class IncomingRequests @Inject constructor() {
    private val _pending = MutableStateFlow<IncomingRequest?>(null)
    val pending: StateFlow<IncomingRequest?> = _pending.asStateFlow()

    fun post(request: IncomingRequest) {
        _pending.value = request
    }

    fun consume(request: IncomingRequest) {
        _pending.compareAndSet(request, null)
    }

    companion object {
        /** A lone web address is checked as a link; anything else as a message. */
        fun analyzeText(text: String, source: EventSource): IncomingRequest {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return IncomingRequest.Error("There was no text to check.")
            val isSingleUrl = !trimmed.any { it.isWhitespace() } && UrlExtractor.parseSingle(trimmed) != null
            return IncomingRequest.Analyze(if (isSingleUrl) EventType.URL_EVENT else EventType.MANUAL_TEXT_EVENT, trimmed.take(5_000), source)
        }
    }
}
