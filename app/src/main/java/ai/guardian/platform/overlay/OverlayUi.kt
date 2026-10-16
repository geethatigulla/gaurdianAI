package ai.guardian.platform.overlay

import ai.guardian.core.designsystem.color
import ai.guardian.core.designsystem.containerColor
import ai.guardian.ui.actionBullets
import ai.guardian.ui.shortTitle
import ai.guardian.ui.verdict
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The draggable floating shield. Tap = scan screen, long-press = menu. */
@Composable
fun FloatingBubble(
    scanning: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        Modifier
            .padding(6.dp)
            .size(56.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() }) }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, amount ->
                    change.consume()
                    onDrag(amount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (scanning) {
            CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
        } else {
            Icon(Icons.Filled.Shield, contentDescription = "Guardian: tap to scan this screen", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
        }
    }
}

/** Dimmed full-screen layer with a bottom card; tapping outside the card closes it. */
@Composable
fun OverlaySheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding()
                .clickable(interactionSource = cardInteraction, indication = null) {},
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(20.dp)) { content() }
        }
    }
}

@Composable
fun ScanResultCard(outcome: ScreenScanOutcome, onDetails: (String) -> Unit, onClose: () -> Unit) {
    val record = outcome.record
    if (record == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text("Nothing to check here", style = MaterialTheme.typography.titleLarge)
                Text("Open a message, link or QR code, then tap the shield.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        return
    }

    val r = record.result
    val v = r.verdict()
    val neutral = r.indicators.isEmpty()
    val tint = if (neutral) MaterialTheme.colorScheme.onSurfaceVariant else r.riskLevel.color()
    val container = if (neutral) MaterialTheme.colorScheme.surfaceVariant else r.riskLevel.containerColor()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
            Icon(v.icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(v.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tint)
            Text(
                if (outcome.foundQr) "${v.subtitle} · QR code found" else v.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (r.indicators.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.indicators.distinctBy { it.shortTitle() }.take(3).forEach { ind ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(ind.severity.color()))
                    Text(ind.shortTitle(), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    r.actionBullets().firstOrNull()?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }

    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Close") }
        Button(onClick = { onDetails(r.eventId) }, modifier = Modifier.weight(1f)) { Text("Details") }
    }
}

/** Shown over the browser when the open website looks dangerous. */
@Composable
fun LinkWarningCard(record: ai.guardian.data.repo.ScanRecord, onGoBack: () -> Unit, onContinue: () -> Unit, onDetails: () -> Unit) {
    val r = record.result
    val v = r.verdict()
    val tint = r.riskLevel.color()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(r.riskLevel.containerColor()), contentAlignment = Alignment.Center) {
            Icon(v.icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text("Risky website", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tint)
            Text(record.displayLabel.removePrefix("Link · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        r.indicators.distinctBy { it.shortTitle() }.take(3).forEach { ind ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(ind.severity.color()))
                Text(ind.shortTitle(), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("Don't enter passwords, OTPs, card or UPI details here.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(18.dp))
    Button(onClick = onGoBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Go back") }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = onContinue, modifier = Modifier.weight(1f)) { Text("Continue anyway") }
        OutlinedButton(onClick = onDetails, modifier = Modifier.weight(1f)) { Text("Why?") }
    }
}

enum class BubbleAction(val label: String, val icon: ImageVector) {
    SCAN_SCREEN("Scan this screen", Icons.Outlined.DocumentScanner),
    CHECK_COPIED("Check copied text", Icons.Outlined.ContentPaste),
    SCAN_QR_CAMERA("Scan QR with camera", Icons.Outlined.QrCodeScanner),
    OPEN_APP("Open Guardian", Icons.Outlined.OpenInNew),
    HIDE("Hide floating button", Icons.Outlined.VisibilityOff),
}

@Composable
fun BubbleMenu(onAction: (BubbleAction) -> Unit) {
    Text("Guardian", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
    BubbleAction.entries.forEach { action ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onAction(action) }.padding(vertical = 14.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(action.label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
