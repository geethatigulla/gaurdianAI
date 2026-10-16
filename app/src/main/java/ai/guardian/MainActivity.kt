package ai.guardian

import ai.guardian.core.designsystem.GuardianTheme
import ai.guardian.data.prefs.ThemeMode
import ai.guardian.engine.model.EventSource
import ai.guardian.feature.onboarding.OnboardingScreen
import ai.guardian.feature.scan.InputMode
import ai.guardian.navigation.GuardianRoot
import ai.guardian.platform.incoming.IncomingRequest
import ai.guardian.platform.incoming.IncomingRequests
import ai.guardian.platform.lock.AppLock
import ai.guardian.platform.lock.LockScreen
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single activity. Besides the launcher it is the entry point for everything other apps hand us:
 * shared text/links/images, "Check with Guardian" from the text-selection menu, widget/tile/shortcut
 * actions and alert notifications.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var incoming: IncomingRequests

    /** Clipboard can only be read once the window has focus (Android 10+). */
    private var checkClipboardOnFocus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val theme = (state as? AppState.Ready)?.theme ?: ThemeMode.SYSTEM
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            GuardianTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (val s = state) {
                        AppState.Loading -> Unit
                        is AppState.Ready -> when {
                            !s.onboardingDone -> OnboardingScreen(onFinished = viewModel::completeOnboarding)
                            s.appLock && !viewModel.unlocked -> LockScreen(viewModel.unlockError, ::unlock)
                            else -> GuardianRoot()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val away = viewModel.backgroundedAt
        if (away != 0L && System.currentTimeMillis() - away > AppLock.RELOCK_AFTER_MS) viewModel.unlocked = false
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.backgroundedAt = System.currentTimeMillis()
    }

    private fun unlock() {
        viewModel.unlockError = null
        AppLock.authenticate(
            this,
            title = "Unlock Guardian",
            onSuccess = { viewModel.unlocked = true },
            onFailure = { viewModel.unlockError = it },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && checkClipboardOnFocus) {
            checkClipboardOnFocus = false
            val clip = getSystemService(ClipboardManager::class.java)?.primaryClip
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            incoming.post(
                if (text.isNullOrBlank()) IncomingRequest.Error("Nothing is copied right now. Copy a message or link, then try again.")
                else IncomingRequests.analyzeText(text, EventSource.CLIPBOARD),
            )
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_RESULT_ID)?.let {
            incoming.post(IncomingRequest.OpenResult(it))
            return
        }
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val type = intent.type.orEmpty()
                if (type.startsWith("image/")) {
                    val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    incoming.post(if (uri != null) IncomingRequest.DecodeQrImage(uri) else IncomingRequest.Error("Couldn't open the shared image."))
                } else {
                    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
                    val combined = if (!subject.isNullOrBlank() && !text.contains(subject)) "$subject\n$text" else text
                    incoming.post(IncomingRequests.analyzeText(combined, EventSource.SHARE_INTENT))
                }
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
                incoming.post(IncomingRequests.analyzeText(text, EventSource.PROCESS_TEXT))
            }
            ACTION_SCAN_QR -> incoming.post(IncomingRequest.OpenQrScanner)
            ACTION_CHECK_MESSAGE -> incoming.post(IncomingRequest.OpenInput(InputMode.TEXT))
            ACTION_CHECK_CLIPBOARD -> checkClipboardOnFocus = true
        }
    }

    companion object {
        const val EXTRA_RESULT_ID = "ai.guardian.extra.RESULT_ID"
        const val ACTION_SCAN_QR = "ai.guardian.action.SCAN_QR"
        const val ACTION_CHECK_CLIPBOARD = "ai.guardian.action.CHECK_CLIPBOARD"
        const val ACTION_CHECK_MESSAGE = "ai.guardian.action.CHECK_MESSAGE"
    }
}
