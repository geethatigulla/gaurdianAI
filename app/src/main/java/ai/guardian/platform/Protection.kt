package ai.guardian.platform

import ai.guardian.platform.notifications.MessageNotificationListener
import ai.guardian.platform.overlay.GuardianAccessibilityService
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Live status of each automatic-protection channel, read from the OS (not from our own prefs). */
data class ProtectionStatus(
    val smsPermission: Boolean,
    val notificationAccess: Boolean,
    val canPostAlerts: Boolean,
    /** Accessibility service on = floating scan button available. */
    val accessibility: Boolean,
)

object Protection {

    fun status(context: Context) = ProtectionStatus(
        smsPermission = granted(context, Manifest.permission.RECEIVE_SMS),
        notificationAccess = hasNotificationAccess(context),
        canPostAlerts = (Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        accessibility = isAccessibilityEnabled(context),
    )

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, GuardianAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationAccess(context: Context) =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** System screen where the user grants Guardian notification access. */
    fun notificationAccessIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, MessageNotificationListener::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    /** App info page — where Android 13+ hides "Allow restricted settings" for sideloaded apps. */
    fun appInfoIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}
