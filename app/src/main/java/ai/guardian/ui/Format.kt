package ai.guardian.ui

import ai.guardian.engine.model.EventType
import android.text.format.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.ui.graphics.vector.ImageVector

fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(epochMillis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

fun EventType.icon(): ImageVector = when (this) {
    EventType.SMS_EVENT -> Icons.Outlined.Sms
    EventType.APP_MESSAGE_EVENT -> Icons.Outlined.Chat
    EventType.MANUAL_TEXT_EVENT -> Icons.Outlined.TextSnippet
    EventType.URL_EVENT -> Icons.Outlined.Link
    EventType.QR_EVENT -> Icons.Outlined.QrCode2
    EventType.CALL_EVENT -> Icons.Outlined.Phone
    EventType.IP_EVENT, EventType.DOMAIN_EVENT -> Icons.Outlined.Dns
}

fun EventType.label(): String = when (this) {
    EventType.SMS_EVENT -> "SMS"
    EventType.APP_MESSAGE_EVENT -> "App message"
    EventType.MANUAL_TEXT_EVENT -> "Message"
    EventType.URL_EVENT -> "Link"
    EventType.QR_EVENT -> "QR code"
    EventType.CALL_EVENT -> "Phone number"
    EventType.IP_EVENT -> "IP address"
    EventType.DOMAIN_EVENT -> "Domain"
}
