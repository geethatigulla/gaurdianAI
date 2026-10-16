package ai.guardian.feature.assistant

import ai.guardian.core.designsystem.GuardianCard
import ai.guardian.core.designsystem.RiskBadge
import ai.guardian.data.repo.ScanRepository
import ai.guardian.data.repo.ScanSummary
import ai.guardian.engine.model.Category
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private data class Way(val icon: ImageVector, val text: String)

private val ways = listOf(
    Way(Icons.Outlined.TouchApp, "Tap the floating shield in any app"),
    Way(Icons.Outlined.Share, "Share a message, link or QR screenshot to Guardian"),
    Way(Icons.Outlined.TextFields, "Select text → \"Check with Guardian\""),
    Way(Icons.Outlined.Link, "Set Guardian as browser to check every link"),
    Way(Icons.Outlined.Widgets, "Widget, Quick Settings tile, or long-press the app icon"),
    Way(Icons.Outlined.ContentPaste, "Copy anything, then \"Check copied\""),
)

private data class Faq(val question: String, val answer: String)

/** Static, reviewed guidance. */
private val faqs = listOf(
    Faq("I clicked a suspicious link", "Close the page without typing anything. If you entered a password, change it in the official app. If you entered bank or card details, call your bank now."),
    Faq("I shared an OTP or UPI PIN", "Call your bank's helpline to block transactions, change your UPI PIN, and report on 1930 or cybercrime.gov.in right away."),
    Faq("I already paid a scammer", "Call 1930 immediately and file at cybercrime.gov.in. Keep screenshots, the UPI ID or account number and the transaction ID."),
    Faq("\"You are under digital arrest\"", "This doesn't exist. Police, CBI or customs never arrest people on video calls or ask for money. Hang up and report on 1930."),
    Faq("Asked to install AnyDesk / share screen", "Don't. It lets them see your OTPs and control your phone. If installed, uninstall it and call your bank."),
    Faq("How do I report fraud?", "Financial fraud: 1930 or cybercrime.gov.in. Fraud calls/SMS: Chakshu on the Sanchar Saathi portal."),
    Faq("Why \"reputation not checked\"?", "This version works offline. It checks warning signs inside the message or link, but can't look up whether it was reported before."),
)

@HiltViewModel
class HelpViewModel @Inject constructor(repository: ScanRepository) : ViewModel() {
    val lastScan: StateFlow<ScanSummary?> = repository.observeHistory().map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit, onOpenResult: (String) -> Unit, viewModel: HelpViewModel = hiltViewModel()) {
    val lastScan by viewModel.lastScan.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            lastScan?.let { scan ->
                item {
                    GuardianCard(onClick = { onOpenResult(scan.id) }) {
                        Text("Your last check", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Category.displayName(scan.category), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            RiskBadge(scan.riskLevel)
                        }
                    }
                }
            }
            item { Text("Ways to check", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(ways) { way ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(way.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text(way.text, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { Text("What to do if…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(faqs) { FaqCard(it) }
        }
    }
}

@Composable
private fun FaqCard(faq: Faq) {
    var expanded by rememberSaveable(faq.question) { mutableStateOf(false) }
    GuardianCard(modifier = Modifier.animateContentSize(), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(faq.question, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
        }
        if (expanded) Text(faq.answer, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
