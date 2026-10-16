package ai.guardian.feature.about

import ai.guardian.BuildConfig
import ai.guardian.core.designsystem.GuardianCard
import ai.guardian.core.designsystem.SectionHeader
import ai.guardian.engine.risk.ScoringConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val limitations = listOf(
    "Rules are English-only. Hindi, Telugu and transliterated (Hinglish) scam messages may be missed.",
    "No online reputation checks: Guardian can't tell whether a link, number or UPI ID has been reported before.",
    "Messages from WhatsApp and other apps are read from their notifications, so muted chats, hidden notification text or very long messages may be missed or cut short.",
    "Automatic SMS checks warn you alongside your messaging app. Android doesn't let Guardian block, hide or delay an SMS.",
    "Guardian can't connect to other QR-scanner apps; use its own scanner, the tile or the widget. Number lookup arrives in a later build.",
    "A low score means no known warning signs were found — not that something is safe. New scams can look clean.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val config = ScoringConfig.DEFAULT
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuardianCard {
                Text("Guardian AI ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
                Text("Scoring version ${config.version}", style = MaterialTheme.typography.bodyMedium)
                Text("Everything runs on this phone. No account, no uploads.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionHeader("How the score works")
            Text(
                "Each warning sign belongs to a family (for example urgency, OTP requests or look-alike links) and is rated weak, medium or strong. " +
                    "Only the strongest sign in each family counts, so repeating \"urgent\" three times doesn't triple the score. " +
                    "Families are combined so that independent signs add up without ever passing 100. " +
                    "Weak signs on their own can never produce more than a Guarded result.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Weights: weak ${config.weight(ai.guardian.engine.model.Severity.WEAK)}, " +
                    "medium ${config.weight(ai.guardian.engine.model.Severity.MEDIUM)}, " +
                    "strong ${config.weight(ai.guardian.engine.model.Severity.STRONG)}. " +
                    "Bands: 0–19 Low, 20–39 Guarded, 40–59 Moderate, 60–79 High, 80–100 Critical.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "These scores are not calibrated probabilities. The bands are categories chosen by hand and have not been measured against real-world data.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Evidence quality is separate from risk: it shows how many of the relevant checks could actually run.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SectionHeader("What Guardian can't do yet")
            limitations.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }

            SectionHeader("Need help now?")
            Text(
                "If you've lost money to fraud in India, call the National Cyber Crime Helpline 1930 or report at cybercrime.gov.in.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
