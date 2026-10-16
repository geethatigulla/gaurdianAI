package ai.guardian.platform.shortcuts

import ai.guardian.MainActivity
import ai.guardian.R
import android.app.PendingIntent
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

private fun mainIntent(context: Context, action: String) =
    Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

private fun mainPendingIntent(context: Context, action: String, requestCode: Int) =
    PendingIntent.getActivity(context, requestCode, mainIntent(context, action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

/** Home-screen widget: one tap to scan a QR or check whatever was just copied. */
class GuardianWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_actions).apply {
            setOnClickPendingIntent(R.id.widget_scan_qr, mainPendingIntent(context, MainActivity.ACTION_SCAN_QR, 1))
            setOnClickPendingIntent(R.id.widget_check_copied, mainPendingIntent(context, MainActivity.ACTION_CHECK_CLIPBOARD, 2))
            setOnClickPendingIntent(R.id.widget_title, mainPendingIntent(context, Intent.ACTION_MAIN, 3))
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

/** Quick Settings tile that opens the QR scanner from anywhere. */
class ScanQrTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = mainIntent(this, MainActivity.ACTION_SCAN_QR)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(mainPendingIntent(this, MainActivity.ACTION_SCAN_QR, 4))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

object QuickAccess {
    fun canPinWidget(context: Context) = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun pinWidget(context: Context): Boolean =
        AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, GuardianWidgetProvider::class.java), null, null)

    fun canAddTile() = Build.VERSION.SDK_INT >= 33

    fun addTile(context: Context, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) return onResult(false)
        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(context, ScanQrTileService::class.java),
            context.getString(R.string.tile_scan_qr),
            Icon.createWithResource(context, R.drawable.ic_qr_scan),
            ContextCompat.getMainExecutor(context),
        ) { result -> onResult(result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) }
    }
}
