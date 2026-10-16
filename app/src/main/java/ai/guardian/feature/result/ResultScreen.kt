package ai.guardian.feature.result

import ai.guardian.core.designsystem.EvidenceTextStyle
import ai.guardian.core.designsystem.color
import ai.guardian.core.designsystem.containerColor
import ai.guardian.core.designsystem.label
import ai.guardian.data.repo.ScanRecord
import ai.guardian.data.repo.UserFeedback
import ai.guardian.engine.model.Check
import ai.guardian.engine.model.CheckStatus
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.RiskLevel
import ai.guardian.ui.actionBullets
import ai.guardian.ui.relativeTime
import ai.guardian.ui.shortTitle
import ai.guardian.ui.verdict
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onScanAnother: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state) { if (state is ResultUiState.Deleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if ((state as? ResultUiState.Loaded)?.record?.savedToHistory == true) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete from history") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ResultUiState.Loading, ResultUiState.Deleted -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ResultUiState.NotFound -> Text(
                    "This result is no longer available.",
                    Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                )
                is ResultUiState.Loaded -> ResultContent(s.record, onScanAnother, viewModel::setFeedback, viewModel::trustDomain, viewModel::reportNumber)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this result?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ResultContent(record: ScanRecord, onScanAnother: () -> Unit, onFeedback: (UserFeedback) -> Unit, onTrust: (String) -> Unit, onReport: (String) -> Unit) {
    val r = record.result
    var showDetails by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { VerdictBanner(record) }

        record.openableUrl?.let { url -> item { OpenLinkButton(url, r.riskLevel) } }

        item {
            Section("What to do") {
                r.actionBullets().forEach { line ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, Modifier.padding(top = 2.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(line, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (r.indicators.isNotEmpty()) {
            item {
                Section("Why (${r.indicators.size})") {
                    r.indicators.forEachIndexed { i, indicator ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        IndicatorRow(indicator)
                    }
                }
            }
            if (record.linkDomains.isNotEmpty()) item { TrustLinks(record.linkDomains, onTrust) }
        }
        if (record.phoneNumbers.isNotEmpty()) item { ReportNumbers(record.phoneNumbers, onReport) }

        item {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { showDetails = !showDetails }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Details", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(if (showDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showDetails) {
            item { DetailsContent(record, onFeedback) }
        }

        item {
            OutlinedButton(onClick = onScanAnother, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Check something else") }
        }
    }
}

@Composable
private fun VerdictBanner(record: ScanRecord) {
    val r = record.result
    val v = r.verdict()
    val neutral = r.indicators.isEmpty()
    val container = if (neutral) MaterialTheme.colorScheme.surfaceVariant else r.riskLevel.containerColor()
    val tint = if (neutral) MaterialTheme.colorScheme.onSurfaceVariant else r.riskLevel.color()

    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(v.icon, contentDescription = null, tint = tint, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(v.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = tint)
            Text(v.subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                if (neutral) record.displayLabel else "Risk ${r.riskScore}/100 · ${record.displayLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** "This is my own site" escape hatch for false alarms on links the user knows. */
@Composable
private fun TrustLinks(domains: List<String>, onTrust: (String) -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    var trusted by remember { mutableStateOf(emptySet<String>()) }
    Column {
        Text("Know this website?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        domains.forEach { domain ->
            TextButton(onClick = { pending = domain }, enabled = domain !in trusted) {
                Text(if (domain in trusted) "Trusted: $domain" else "Trust $domain")
            }
        }
    }
    pending?.let { domain ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Trust $domain?") },
            text = { Text("Links to $domain won't be checked again. Only do this for your own or well-known sites. Scammers copy real-looking names.") },
            confirmButton = {
                TextButton(onClick = {
                    onTrust(domain)
                    trusted = trusted + domain
                    pending = null
                    Toast.makeText(context, "$domain trusted", Toast.LENGTH_SHORT).show()
                }) { Text("Trust") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

/** Lets the user build their own scam-number list: flagged next time, optionally blocked. */
@Composable
private fun ReportNumbers(numbers: List<String>, onReport: (String) -> Unit) {
    val context = LocalContext.current
    var reported by remember { mutableStateOf(emptySet<String>()) }
    Column {
        Text("Scam number?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        numbers.forEach { number ->
            TextButton(onClick = {
                onReport(number)
                reported = reported + number
                Toast.makeText(context, "$number reported", Toast.LENGTH_SHORT).show()
            }, enabled = number !in reported) {
                Text(if (number in reported) "Reported: $number" else "Report $number")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
        }
    }
}

/** Short title; tap for the full explanation. */
@Composable
private fun IndicatorRow(indicator: Indicator) {
    var expanded by rememberSaveable(indicator.type, indicator.matchedText) { mutableStateOf(false) }
    val tint = indicator.severity.color()
    Column(Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded }.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
            Text(indicator.shortTitle(), Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (expanded) "Less" else "More", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            Text(indicator.description, Modifier.padding(start = 22.dp, top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            indicator.matchedText?.let {
                Surface(Modifier.padding(start = 22.dp, top = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Text(it, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = EvidenceTextStyle, maxLines = 3)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsContent(record: ScanRecord, onFeedback: (UserFeedback) -> Unit) {
    val r = record.result
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(r.summary, style = MaterialTheme.typography.bodyMedium)

        Section("Checks · ${r.evidenceQuality.label().lowercase()}") {
            r.checks.forEach { CheckRow(it) }
        }

        Column {
            Text("Was this right?", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserFeedback.entries.forEach { fb ->
                    FilterChip(selected = record.userFeedback == fb, onClick = { onFeedback(fb) }, label = { Text(fb.label) })
                }
            }
        }

        record.rawInputRedacted?.let { text ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Text(text, Modifier.padding(12.dp), style = EvidenceTextStyle)
            }
        }

        Text(
            "Scoring ${r.scoringVersion} · rule-based, not calibrated · ${relativeTime(r.analyzedAt)}" +
                if (!record.savedToHistory) " · not saved (history off)" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CheckRow(check: Check) {
    val (icon, tint, label) = checkVisual(check.status)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(check.name, style = MaterialTheme.typography.bodyMedium)
            Text(check.note ?: label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun checkVisual(status: CheckStatus): Triple<ImageVector, Color, String> = when (status) {
    CheckStatus.COMPLETED -> Triple(Icons.Outlined.CheckCircle, RiskLevel.LOW.color(), "Done")
    CheckStatus.UNAVAILABLE -> Triple(Icons.Outlined.HelpOutline, MaterialTheme.colorScheme.onSurfaceVariant, "Not available")
    CheckStatus.SKIPPED -> Triple(Icons.Outlined.RemoveCircleOutline, MaterialTheme.colorScheme.onSurfaceVariant, "Skipped")
    CheckStatus.FAILED -> Triple(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error, "Failed")
}

/** Opens the link in the user's browser after they've seen the result. */
@Composable
private fun OpenLinkButton(url: String, level: RiskLevel) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val risky = level >= RiskLevel.MODERATE
    val open = {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            context.startActivity(view)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No browser app found", Toast.LENGTH_SHORT).show()
        }
    }
    if (risky) {
        OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Open link anyway") }
    } else {
        Button(onClick = open, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Open link") }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Open a risky link?") },
            text = { Text("Don't enter passwords, OTPs, card or UPI details on the page.") },
            confirmButton = { TextButton(onClick = { confirm = false; open() }) { Text("Open anyway") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Don't open") } },
        )
    }
}
