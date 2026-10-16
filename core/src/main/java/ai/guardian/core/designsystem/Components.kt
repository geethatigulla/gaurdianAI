package ai.guardian.core.designsystem

import ai.guardian.engine.model.EvidenceQuality
import ai.guardian.engine.model.RiskLevel
import ai.guardian.engine.model.Severity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---- Risk helpers ---------------------------------------------------------------------------

fun RiskLevel.label(): String = when (this) {
    RiskLevel.LOW -> "Low"
    RiskLevel.GUARDED -> "Guarded"
    RiskLevel.MODERATE -> "Moderate"
    RiskLevel.HIGH -> "High"
    RiskLevel.CRITICAL -> "Critical"
}

@Composable
fun RiskLevel.color(): Color = with(GuardianTheme.riskColors) {
    when (this@color) {
        RiskLevel.LOW -> low
        RiskLevel.GUARDED -> guarded
        RiskLevel.MODERATE -> moderate
        RiskLevel.HIGH -> high
        RiskLevel.CRITICAL -> critical
    }
}

@Composable
fun RiskLevel.containerColor(): Color = with(GuardianTheme.riskColors) {
    when (this@containerColor) {
        RiskLevel.LOW -> lowContainer
        RiskLevel.GUARDED -> guardedContainer
        RiskLevel.MODERATE -> moderateContainer
        RiskLevel.HIGH -> highContainer
        RiskLevel.CRITICAL -> criticalContainer
    }
}

@Composable
fun Severity.color(): Color = when (this) {
    Severity.WEAK -> GuardianTheme.riskColors.guarded
    Severity.MEDIUM -> GuardianTheme.riskColors.moderate
    Severity.STRONG -> GuardianTheme.riskColors.critical
}

fun Severity.label(): String = when (this) {
    Severity.WEAK -> "Weak sign"
    Severity.MEDIUM -> "Medium sign"
    Severity.STRONG -> "Strong sign"
}

fun EvidenceQuality.label(): String = when (this) {
    EvidenceQuality.LOW -> "Limited evidence"
    EvidenceQuality.MODERATE -> "Moderate evidence"
    EvidenceQuality.HIGH -> "Full evidence"
}

// ---- Components -----------------------------------------------------------------------------

/** Pill showing a risk band. Always shows text — colour is never the only signal. */
@Composable
fun RiskBadge(level: RiskLevel, modifier: Modifier = Modifier, text: String = "${level.label()} risk") {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = level.containerColor(),
        contentColor = level.color(),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(level.color()))
            Text(text, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Circular 0–100 score meter. */
@Composable
fun RiskGauge(score: Int, level: RiskLevel, modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val progress by animateFloatAsState(targetValue = score / 100f, animationSpec = tween(700), label = "gauge")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tint = level.color()
    Box(
        modifier = modifier.size(size).semantics { contentDescription = "Risk score $score out of 100, ${level.label()}" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, startAngle = 135f, sweepAngle = 270f, useCenter = false, style = stroke)
            drawArc(tint, startAngle = 135f, sweepAngle = 270f * progress, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.displaySmall, color = tint, fontWeight = FontWeight.Bold)
            Text("of 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GuardianCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors) { Column(Modifier.padding(16.dp), content = content) }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors) { Column(Modifier.padding(16.dp), content = content) }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.let { Box(Modifier.padding(top = 8.dp)) { it() } }
    }
}

/** Inline honest-limitation note, e.g. "External reputation not checked". */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
