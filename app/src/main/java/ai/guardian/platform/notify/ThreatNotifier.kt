package ai.guardian.platform.notify

import ai.guardian.MainActivity
import ai.guardian.R
import ai.guardian.core.designsystem.label
import ai.guardian.data.repo.ScanRecord
import ai.guardian.engine.model.Category
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Posts the "suspicious message" alert. Never puts message text in the notification. */
@Singleton
class ThreatNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * [kind] is shown in the title, e.g. "SMS" or "WhatsApp message".
     * Returns false if the alert could not be shown (permission missing or notifications off).
     */
    fun notifySuspicious(record: ScanRecord, kind: String, sender: String?, hideOnLockScreen: Boolean): Boolean {
        if (!canNotify()) return false
        ensureChannel()
        val result = record.result
        val title = "Suspicious $kind · ${result.riskLevel.label()} risk (${result.riskScore})"
        val text = Category.displayName(result.category) + (sender?.let { " · from $it" } ?: "") + ". Tap to see why."

        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_RESULT_ID, result.eventId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, result.eventId.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_guardian)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text Don't click links or share OTP/PIN until you've checked."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)

        if (hideOnLockScreen) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_guardian)
                    .setContentTitle("Guardian AI")
                    .setContentText("A message needs your attention")
                    .build(),
            )
        } else {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        return try {
            NotificationManagerCompat.from(context).notify(result.eventId.hashCode(), builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Scam alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Warnings when an incoming message looks like a scam"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "threat_alerts"
    }
}
