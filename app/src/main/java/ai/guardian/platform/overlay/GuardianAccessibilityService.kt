package ai.guardian.platform.overlay

import ai.guardian.MainActivity
import ai.guardian.core.designsystem.GuardianTheme
import ai.guardian.data.repo.SettingsRepository
import ai.guardian.platform.qr.QrDecoder
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Hosts the floating shield that sits on top of every app. Tapping it reads the visible screen text
 * and takes a screenshot to find QR codes, checks both on-device and shows the verdict in a card on
 * top of the current app. Accessibility overlays need no "display over other apps" permission.
 * The service only reads the screen when the user taps the shield — it ignores all other events.
 */
@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var screenScan: ScreenScanUseCase
    @Inject lateinit var linkGuard: LinkGuard

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner = OverlayOwner()
    private lateinit var windowManager: WindowManager

    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var sheetView: ComposeView? = null

    private var scanning by mutableStateOf(false)
    private var linkGuardOn = true
    private var lastBrowserCheck = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        owner.start()
        scope.launch {
            settings.settings.map { it.floatingButton }.distinctUntilChanged().collect { show ->
                if (show) showBubble() else hideBubble()
            }
        }
        scope.launch { settings.settings.map { it.linkGuard }.distinctUntilChanged().collect { linkGuardOn = it } }
    }

    /** Only browser events arrive here (see packageNames in the config): read the address bar. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (!linkGuardOn || sheetView != null) return
        val now = System.currentTimeMillis()
        if (now - lastBrowserCheck < 700) return
        lastBrowserCheck = now
        val root = rootInActiveWindow ?: return
        val address = LinkGuard.readAddressBar(root, pkg) ?: return
        scope.launch {
            val record = runCatching { withContext(Dispatchers.Default) { linkGuard.check(address) } }.getOrNull() ?: return@launch
            showSheet {
                LinkWarningCard(
                    record = record,
                    onGoBack = {
                        hideSheet()
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    },
                    onContinue = ::hideSheet,
                    onDetails = { hideSheet(); openApp(MainActivity.EXTRA_RESULT_ID, record.result.eventId) },
                )
            }
        }
    }
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideSheet()
        hideBubble()
        owner.stop()
        scope.cancel()
        super.onDestroy()
    }

    // ---- Bubble ------------------------------------------------------------------------------

    private fun showBubble() {
        if (bubbleView != null) return
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val metrics = resources.displayMetrics
        val params = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, focusable = false).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, metrics.widthPixels - dp(68))
            y = prefs.getInt(KEY_Y, metrics.heightPixels / 3)
        }
        val view = composeView {
            FloatingBubble(
                scanning = scanning,
                onTap = ::scanScreen,
                onLongPress = ::showMenu,
                onDrag = { delta ->
                    params.x += delta.x.roundToInt()
                    params.y += delta.y.roundToInt()
                    bubbleView?.let { windowManager.updateViewLayout(it, params) }
                },
                onDragEnd = ::snapBubbleToEdge,
            )
        }
        windowManager.addView(view, params)
        bubbleView = view
        bubbleParams = params
    }

    private fun hideBubble() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
    }

    private fun snapBubbleToEdge() {
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        val metrics = resources.displayMetrics
        params.x = if (params.x + view.width / 2 < metrics.widthPixels / 2) 0 else metrics.widthPixels - view.width
        params.y = params.y.coerceIn(dp(48), metrics.heightPixels - view.height - dp(48))
        windowManager.updateViewLayout(view, params)
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
    }

    // ---- Scan --------------------------------------------------------------------------------

    private fun scanScreen() {
        if (scanning) return
        hideSheet()
        scanning = true
        scope.launch {
            val text = runCatching { ScreenReader.visibleText(this@GuardianAccessibilityService) }.getOrDefault("")
            val qr = captureQrCode()
            val outcome = runCatching { withContext(Dispatchers.Default) { screenScan.scan(text, qr) } }.getOrElse { ScreenScanOutcome(null, false) }
            scanning = false
            showSheet { ScanResultCard(outcome, onDetails = { id -> hideSheet(); openApp(MainActivity.EXTRA_RESULT_ID, id) }, onClose = ::hideSheet) }
        }
    }

    /** Screenshot (Android 11+) → ZXing. The bubble is hidden briefly so it doesn't cover a code. */
    private suspend fun captureQrCode(): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        bubbleView?.visibility = View.INVISIBLE
        delay(80)
        val bitmap = suspendCancellableCoroutine<Bitmap?> { cont ->
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    result.hardwareBuffer.close()
                    if (cont.isActive) cont.resume(soft)
                }

                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }
        bubbleView?.visibility = View.VISIBLE
        return bitmap?.let { withContext(Dispatchers.Default) { QrDecoder.decode(it) } }
    }

    // ---- Sheet (result card / menu) ----------------------------------------------------------

    private fun showMenu() {
        showSheet {
            BubbleMenu { action ->
                hideSheet()
                when (action) {
                    BubbleAction.SCAN_SCREEN -> scanScreen()
                    BubbleAction.CHECK_COPIED -> openApp(action = MainActivity.ACTION_CHECK_CLIPBOARD)
                    BubbleAction.SCAN_QR_CAMERA -> openApp(action = MainActivity.ACTION_SCAN_QR)
                    BubbleAction.OPEN_APP -> openApp()
                    BubbleAction.HIDE -> scope.launch { settings.setFloatingButton(false) }
                }
            }
        }
    }

    private fun showSheet(content: @androidx.compose.runtime.Composable () -> Unit) {
        hideSheet()
        val params = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, focusable = false)
        val view = composeView { OverlaySheet(onDismiss = ::hideSheet, content = content) }
        windowManager.addView(view, params)
        sheetView = view
    }

    private fun hideSheet() {
        sheetView?.let { runCatching { windowManager.removeView(it) } }
        sheetView = null
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private fun openApp(extraKey: String? = null, extraValue: String? = null, action: String = Intent.ACTION_MAIN) {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (extraKey != null) intent.putExtra(extraKey, extraValue)
        startActivity(intent)
    }

    private fun composeView(content: @androidx.compose.runtime.Composable () -> Unit) = ComposeView(this).apply {
        owner.attach(this)
        setContent {
            val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            GuardianTheme(darkTheme = dark) { content() }
        }
    }

    private fun overlayParams(width: Int, height: Int, focusable: Boolean) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val PREFS = "floating_bubble"
        const val KEY_X = "x"
        const val KEY_Y = "y"
    }
}
