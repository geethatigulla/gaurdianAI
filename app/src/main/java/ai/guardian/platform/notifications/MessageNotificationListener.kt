package ai.guardian.platform.notifications

import ai.guardian.data.repo.AnalyzeScanUseCase
import ai.guardian.data.repo.SettingsRepository
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.platform.notify.ThreatNotifier
import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reads the text of new message notifications from messaging apps (WhatsApp, Telegram, Gmail…)
 * once the user grants notification access, and checks them on-device. This is the only way
 * Android lets a third-party app see messages arriving in other apps. Guardian cannot block,
 * hide or delay them — it only warns.
 */
@AndroidEntryPoint
class MessageNotificationListener : NotificationListenerService() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var analyze: AnalyzeScanUseCase
    @Inject lateinit var notifier: ThreatNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Messaging apps re-post the same notification as a conversation grows; check each message once.
    private val seen = object : LinkedHashMap<Int, Unit>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Unit>?) = size > 500
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || n.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val knownApp = MessagingApps.nameFor(pkg)
        if (knownApp == null && n.category != Notification.CATEGORY_MESSAGE) return
        val appName = knownApp ?: appLabel(pkg)

        val fresh = NotificationText.messages(n).filter { msg ->
            synchronized(seen) { seen.put(listOf(pkg, msg.sender, msg.text).hashCode(), Unit) == null }
        }
        if (fresh.isEmpty()) return

        scope.launch {
            val prefs = settings.current()
            if (!prefs.appMessageMonitoring) return@launch
            // The SMS receiver already checks SMS; don't double-alert from the SMS app's notification.
            if (prefs.smsMonitoring && pkg == Telephony.Sms.getDefaultSmsPackage(this@MessageNotificationListener)) return@launch

            for (msg in fresh) {
                val record = runCatching {
                    analyze(EventType.APP_MESSAGE_EVENT, msg.text, EventSource.NOTIFICATION, msg.sender, channel = appName)
                }.getOrNull() ?: continue
                if (prefs.notificationsEnabled && record.result.riskScore >= prefs.alertThreshold) {
                    notifier.notifySuspicious(record, "$appName message", msg.sender, prefs.lockScreenPrivacy)
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault("App")
}

object MessagingApps {
    private val known = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "org.telegram.messenger.web" to "Telegram",
        "org.thoughtcrime.securesms" to "Signal",
        "com.facebook.orca" to "Messenger",
        "com.instagram.android" to "Instagram",
        "com.google.android.gm" to "Gmail",
        "com.microsoft.office.outlook" to "Outlook",
        "com.google.android.apps.messaging" to "Messages",
        "com.samsung.android.messaging" to "Samsung Messages",
        "com.truecaller" to "Truecaller",
        "com.snapchat.android" to "Snapchat",
        "jp.naver.line.android" to "LINE",
        "com.discord" to "Discord",
        "com.linkedin.android" to "LinkedIn",
    )

    fun nameFor(pkg: String): String? = known[pkg]
}

data class NotificationMessage(val sender: String?, val text: String)

object NotificationText {
    private val summaryOnly = Regex("^\\d+ new messages?( from \\d+ chats?)?$|^checking for new messages", RegexOption.IGNORE_CASE)

    /** The latest few messages in a notification, newest last. */
    fun messages(n: Notification): List<NotificationMessage> {
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val raw = if (style != null && style.messages.isNotEmpty()) {
            style.messages.takeLast(MAX_PER_NOTIFICATION).map { m ->
                NotificationMessage(m.person?.name?.toString() ?: style.conversationTitle?.toString() ?: title, m.text?.toString().orEmpty())
            }
        } else {
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            listOf(NotificationMessage(title, text))
        }
        return raw.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() && !summaryOnly.containsMatchIn(it.text) }
    }

    private const val MAX_PER_NOTIFICATION = 5
}
