package ai.guardian.feature.home

import ai.guardian.core.designsystem.EmptyState
import ai.guardian.core.designsystem.GuardianTheme
import ai.guardian.data.prefs.UserSettings
import ai.guardian.data.repo.DashboardStats
import ai.guardian.data.repo.ScanRepository
import ai.guardian.data.repo.SettingsRepository
import ai.guardian.feature.scan.InputMode
import ai.guardian.feature.settings.protections
import ai.guardian.platform.Protection
import ai.guardian.ui.ScanRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(val stats: DashboardStats? = null, val settings: UserSettings = UserSettings())

@HiltViewModel
class HomeViewModel @Inject constructor(scans: ScanRepository, settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(scans.observeDashboard(), settings.settings) { stats, prefs -> HomeUiState(stats, prefs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartScan: (InputMode) -> Unit,
    onScanQr: () -> Unit,
    onOpenResult: (String) -> Unit,
    onSeeAllActivity: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var status by remember { mutableStateOf(Protection.status(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = Protection.status(context) }
    val stats = state.stats

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guardian", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = onOpenHelp) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Help") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { ProtectionCard(protections(state.settings, status), onOpenSettings) }

            item {
                Text("Check now", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile("QR code", Icons.Outlined.QrCodeScanner, Modifier.weight(1f), onScanQr)
                    ActionTile("Message", Icons.Outlined.Sms, Modifier.weight(1f)) { onStartScan(InputMode.TEXT) }
                    ActionTile("Link", Icons.Outlined.Link, Modifier.weight(1f)) { onStartScan(InputMode.URL) }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (!stats?.recent.isNullOrEmpty()) TextButton(onClick = onSeeAllActivity) { Text("See all") }
                }
                when {
                    stats == null -> Unit
                    stats.recent.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.Shield,
                        title = "Nothing checked yet",
                        body = "Results will show up here.",
                    )
                    else -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        stats.recent.forEach { scan -> ScanRow(scan, onClick = { onOpenResult(scan.id) }) }
                    }
                }
            }
        }
    }
}

data class ProtectionItem(val label: String, val icon: ImageVector, val on: Boolean)

@Composable
private fun ProtectionCard(items: List<ProtectionItem>, onOpenSettings: () -> Unit) {
    val active = items.count { it.on }
    val all = active == items.size
    val risk = GuardianTheme.riskColors
    val tint = if (all) risk.low else if (active > 0) risk.moderate else risk.neutral
    val container = if (all) risk.lowContainer else if (active > 0) risk.moderateContainer else risk.neutralContainer

    Card(onClick = onOpenSettings, colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (all) Icons.Filled.GppGood else Icons.Filled.GppMaybe, contentDescription = null, tint = tint, modifier = Modifier.size(56.dp))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        when {
                            all -> "Fully protected"
                            active > 0 -> "Partly protected"
                            else -> "Protection is off"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(if (all) "Everything is on" else "$active of ${items.size} on · tap to turn on", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { ProtectionChip(it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ProtectionChip(item: ProtectionItem, modifier: Modifier = Modifier) {
    val risk = GuardianTheme.riskColors
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(item.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Box(
                Modifier.align(Alignment.TopEnd).size(9.dp).clip(CircleShape)
                    .background(if (item.on) risk.low else risk.neutral),
            )
        }
        Text(item.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        Text(if (item.on) "On" else "Off", style = MaterialTheme.typography.labelSmall, color = if (item.on) risk.low else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
