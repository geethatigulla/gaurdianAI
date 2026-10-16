package ai.guardian.feature.qr

import ai.guardian.data.repo.AnalyzeScanUseCase
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.platform.qr.QrDecoder
import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class QrUiState(val checking: Boolean = false, val message: String? = null)

@HiltViewModel
class QrScanViewModel @Inject constructor(
    private val analyze: AnalyzeScanUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(QrUiState())
    val state: StateFlow<QrUiState> = _state.asStateFlow()

    private val _results = Channel<String>(Channel.BUFFERED)
    val results = _results.receiveAsFlow()

    private val handled = AtomicBoolean(false)

    /** Called from the camera thread; only the first decoded code is used. */
    fun onDecoded(payload: String, source: EventSource = EventSource.QR_CAMERA) {
        if (!handled.compareAndSet(false, true)) return
        viewModelScope.launch {
            _state.value = QrUiState(checking = true)
            runCatching { analyze(EventType.QR_EVENT, payload, source) }
                .onSuccess { _results.send(it.result.eventId) }
                .onFailure {
                    handled.set(false)
                    _state.value = QrUiState(message = "Couldn't check this QR code.")
                }
        }
    }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            _state.value = QrUiState(checking = true)
            val payload = withContext(Dispatchers.IO) { QrDecoder.decode(context, uri) }
            if (payload == null) _state.value = QrUiState(message = "No QR code found in that image. Try a clearer or closer picture.")
            else onDecoded(payload, EventSource.QR_IMAGE)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(onBack: () -> Unit, onResult: (String) -> Unit, viewModel: QrScanViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var cameraGranted by remember { mutableStateOf(ai.guardian.platform.Protection.granted(context, Manifest.permission.CAMERA)) }
    var asked by remember { mutableStateOf(false) }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        asked = true
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onImagePicked)
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) cameraPermission.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) { viewModel.results.collect(onResult) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan a QR code") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (cameraGranted) {
                    CameraPreview(onDecoded = viewModel::onDecoded)
                    Box(Modifier.size(240.dp).border(BorderStroke(3.dp, Color.White.copy(alpha = 0.9f)), RoundedCornerShape(20.dp)))
                } else {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (asked) "Camera permission is off. You can still check a QR code from a screenshot or photo."
                            else "Guardian needs the camera to scan QR codes.",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                    }
                }
                if (state.checking) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text("Checking…", Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
            Text(
                state.message ?: "Point at a QR code. Guardian shows what it does before anything is opened or paid.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Check a QR from a screenshot or photo")
            }
        }
    }
}

@Composable
private fun CameraPreview(onDecoded: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { a ->
                    a.setAnalyzer(executor) { image ->
                        image.use { QrDecoder.decode(it)?.let(onDecoded) }
                    }
                }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }
        providerFuture.addListener(listener, androidx.core.content.ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
