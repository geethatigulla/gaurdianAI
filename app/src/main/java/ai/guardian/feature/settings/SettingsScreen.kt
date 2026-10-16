package ai.guardian.feature.settings

import ai.guardian.core.designsystem.InfoNote
import ai.guardian.data.prefs.ThemeMode
import ai.guardian.data.prefs.UserSettings
import ai.guardian.data.repo.ScanRepository
import ai.guardian.data.repo.SettingsRepository
import ai.guardian.feature.home.ProtectionItem
import ai.guardian.platform.Protection
import ai.guardian.platform.ProtectionStatus
import ai.guardian.platform.shortcuts.QuickAccess
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.PhoneDisabled
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val scans: ScanRepository,
) : ViewModel() {
    val state: StateFlow<UserSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setStoreRawText(enabled: Boolean) = viewModelScope.launch { settings.setStoreRawText(enabled) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setTheme(mode) }
    fun setRetention(days: Int) = viewModelScope.launch {
        settings.setRetentionDays(days)
        scans.enforceRetention()
    }
    fun setSmsMonitoring(enabled: Boolean) = viewModelScope.launch { settings.setSmsMonitoring(enabled) }
    fun setAppMessageMonitoring(enabled: Boolean) = viewModelScope.launch { settings.setAppMessageMonitoring(enabled) }
    fun setFloatingButton(enabled: Boolean) = viewModelScope.launch { settings.setFloatingButton(enabled) }
    fun setLinkGuard(enabled: Boolean) = viewModelScope.launch { settings.setLinkGuard(enabled) }
    fun setCallScreening(enabled: Boolean) = viewModelScope.launch { settings.setCallScreening(enabled) }
    fun setBlockReportedCalls(enabled: Boolean) = viewModelScope.launch { settings.setBlockReportedCalls(enabled) }
    fun unreport(number: String) = viewModelScope.launch { settings.unreportNumber(number) }
    fun setAlertThreshold(score: Int) = viewModelScope.launch { settings.setAlertThreshold(score) }
    fun setLockScreenPrivacy(enabled: Boolean) = viewModelScope.launch { settings.setLockScreenPrivacy(enabled) }
    fun untrust(domain: String) = viewModelScope.launch { settings.untrustDomain(domain) }
    fun clearHistory() = viewModelScope.launch { scans.clearAll() }
}

/** The four automatic protections, as shown on Home. On = our setting AND the OS grant. */
fun protections(settings: UserSettings, status: ProtectionStatus) = listOf(
    ProtectionItem("Floating", Icons.Outlined.TouchApp, settings.floatingButton && status.accessibility),
    ProtectionItem("SMS", Icons.Outlined.Sms, settings.smsMonitoring && status.smsPermission),
    ProtectionItem("Apps", Icons.Outlined.Chat, settings.appMessageMonitoring && status.notificationAccess),
    ProtectionItem("Web", Icons.Outlined.Link, settings.linkGuard && status.accessibility),
    ProtectionItem("Calls", Icons.Outlined.Call, settings.callScreening && status.callScreening),
)

private val THRESHOLD_OPTIONS = listOf(40, 60, 80)

private fun thresholdLabel(score: Int) = when (score) {
    40 -> "Suspicious and above"
    60 -> "Likely scams and above"
    80 -> "Only scam alerts"
    else -> "Score $score and above"
}

private fun retentionLabel(days: Int) = when (days) {
    0 -> "Off"
    365 -> "1 year"
    else -> "$days days"
}

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenAbout: () -> Unit, onOpenHelp: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // OS-level grants can change in system settings, so re-read them on every resume.
    var status by remember { mutableStateOf(Protection.status(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = Protection.status(context) }
    var showRestrictedHint by remember { mutableStateOf(false) }

    val smsPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        status = Protection.status(context)
        viewModel.setSmsMonitoring(status.smsPermission)
        if (!status.smsPermission) Toast.makeText(context, "SMS permission denied", Toast.LENGTH_SHORT).show()
    }
    val alertPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { status = Protection.status(context) }
    val systemScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { status = Protection.status(context) }

    fun askAlerts() {
        if (Build.VERSION.SDK_INT >= 33 && !status.canPostAlerts) alertPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        val s = settings ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {

            Header("Protection")

            val floatingOn = s.floatingButton && status.accessibility
            ToggleRow(Icons.Outlined.TouchApp, "Floating button", if (floatingOn) "Tap it in any app to check the screen" else "Off", floatingOn) { enable ->
                viewModel.setFloatingButton(enable)
                if (enable && !status.accessibility) {
                    showRestrictedHint = true
                    Toast.makeText(context, "Find \"Guardian floating button\" and turn it on", Toast.LENGTH_LONG).show()
                    systemScreen.launch(Protection.accessibilitySettingsIntent())
                }
            }

            val smsOn = s.smsMonitoring && status.smsPermission
            ToggleRow(Icons.Outlined.Sms, "SMS", if (smsOn) "Every new SMS is checked" else "Off", smsOn) { enable ->
                if (!enable) viewModel.setSmsMonitoring(false)
                else smsPermissions.launch(
                    buildList {
                        add(Manifest.permission.RECEIVE_SMS)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.toTypedArray(),
                )
            }

            val appsOn = s.appMessageMonitoring && status.notificationAccess
            ToggleRow(Icons.Outlined.Chat, "WhatsApp & other apps", if (appsOn) "New messages are checked" else "Off", appsOn) { enable ->
                viewModel.setAppMessageMonitoring(enable)
                if (enable) {
                    askAlerts()
                    if (!status.notificationAccess) {
                        showRestrictedHint = true
                        systemScreen.launch(Protection.notificationAccessIntent(context))
                    }
                }
            }

            val guardOn = s.linkGuard && status.accessibility
            ToggleRow(Icons.Outlined.Link, "Websites", if (guardOn) "Risky sites in your browser get a warning" else "Off", guardOn) { enable ->
                viewModel.setLinkGuard(enable)
                if (enable && !status.accessibility) {
                    showRestrictedHint = true
                    Toast.makeText(context, "Find \"Guardian floating button\" and turn it on", Toast.LENGTH_LONG).show()
                    systemScreen.launch(Protection.accessibilitySettingsIntent())
                }
            }

            val callsOn = s.callScreening && status.callScreening
            ToggleRow(Icons.Outlined.Call, "Calls", if (callsOn) "Unknown callers are checked before ringing" else "Off", callsOn) { enable ->
                viewModel.setCallScreening(enable)
                if (enable && !status.callScreening) {
                    askAlerts()
                    val intent = Protection.callScreeningRoleIntent(context)
                    if (intent != null) systemScreen.launch(intent)
                    else Toast.makeText(context, "This phone doesn't support call screening apps", Toast.LENGTH_LONG).show()
                }
            }
            if (callsOn) {
                ToggleRow(Icons.Outlined.Block, "Block reported numbers", if (s.blockReportedCalls) "Calls from them are rejected" else "Off · only warn", s.blockReportedCalls, viewModel::setBlockReportedCalls)
            }

            if (showRestrictedHint && (!status.accessibility || !status.notificationAccess)) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    InfoNote("Switch greyed out? Open App info → ⋮ → \"Allow restricted settings\", then try again.")
                    TextButton(onClick = { systemScreen.launch(Protection.appInfoIntent(context)) }) { Text("Open App info") }
                }
            }

            if (smsOn || appsOn || callsOn) {
                ValueRow(Icons.Outlined.NotificationsActive, "Alert me for", thresholdLabel(s.alertThreshold)) { dialog = "threshold" }
                ToggleRow(Icons.Outlined.Lock, "Hide on lock screen", null, s.lockScreenPrivacy, viewModel::setLockScreenPrivacy)
                if (!status.canPostAlerts) {
                    ValueRow(Icons.Outlined.NotificationsActive, "Notifications are off", "Tap to allow alerts") {
                        if (Build.VERSION.SDK_INT >= 33) alertPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else systemScreen.launch(Protection.appInfoIntent(context))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Header("Shortcuts")
            if (QuickAccess.canPinWidget(context)) {
                ValueRow(Icons.Outlined.Widgets, "Add home-screen widget", null) { QuickAccess.pinWidget(context) }
            }
            if (QuickAccess.canAddTile()) {
                ValueRow(Icons.Outlined.QrCodeScanner, "Add Quick Settings tile", null) {
                    QuickAccess.addTile(context) { added -> if (added) Toast.makeText(context, "Tile added", Toast.LENGTH_SHORT).show() }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Header("Privacy")
            ToggleRow(Icons.Outlined.Notes, "Save message text", if (s.storeRawText) "Saved with numbers masked" else "Off", s.storeRawText, viewModel::setStoreRawText)
            ValueRow(Icons.Outlined.VerifiedUser, "Trusted websites", if (s.trustedDomains.isEmpty()) "None" else "${s.trustedDomains.size}") { dialog = "trusted" }
            ValueRow(Icons.Outlined.PhoneDisabled, "Reported numbers", if (s.reportedNumbers.isEmpty()) "None" else "${s.reportedNumbers.size}") { dialog = "reported" }
            ValueRow(Icons.Outlined.History, "Keep history", retentionLabel(s.retentionDays)) { dialog = "retention" }
            ValueRow(Icons.Outlined.DeleteOutline, "Clear history", null, danger = true) { dialog = "clear" }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Header("App")
            ValueRow(Icons.Outlined.DarkMode, "Theme", themeLabel(s.theme)) { dialog = "theme" }
            ValueRow(Icons.AutoMirrored.Outlined.HelpOutline, "Help", null, chevron = true, onClick = onOpenHelp)
            ValueRow(Icons.Outlined.Info, "About & limitations", null, chevron = true, onClick = onOpenAbout)
        }

        when (dialog) {
            "retention" -> ChoiceDialog("Keep history", UserSettings.RETENTION_OPTIONS, s.retentionDays, ::retentionLabel, { viewModel.setRetention(it); dialog = null }) { dialog = null }
            "threshold" -> ChoiceDialog("Alert me for", THRESHOLD_OPTIONS, s.alertThreshold, ::thresholdLabel, { viewModel.setAlertThreshold(it); dialog = null }) { dialog = null }
            "theme" -> ChoiceDialog("Theme", ThemeMode.entries, s.theme, ::themeLabel, { viewModel.setTheme(it); dialog = null }) { dialog = null }
            "trusted" -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Trusted websites") },
                text = {
                    Column {
                        if (s.trustedDomains.isEmpty()) {
                            Text("None yet. Tap Trust on a result for your own sites, like a dev or staging server. Local addresses such as localhost and 192.168.x.x are never flagged.")
                        }
                        s.trustedDomains.sorted().forEach { domain ->
                            ListItem(
                                headlineContent = { Text(domain) },
                                trailingContent = { TextButton(onClick = { viewModel.untrust(domain) }) { Text("Remove") } },
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
            )
            "reported" -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Reported numbers") },
                text = {
                    Column {
                        if (s.reportedNumbers.isEmpty()) Text("None yet. Tap Report on a result with a scam number.")
                        s.reportedNumbers.sorted().forEach { number ->
                            ListItem(
                                headlineContent = { Text(number) },
                                trailingContent = { TextButton(onClick = { viewModel.unreport(number) }) { Text("Remove") } },
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
            )
            "clear" -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Clear all history?") },
                confirmButton = { TextButton(onClick = { viewModel.clearHistory(); dialog = null }) { Text("Clear") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun ValueRow(icon: ImageVector, title: String, value: String?, danger: Boolean = false, chevron: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null, tint = color) },
        headlineContent = { Text(title, color = color) },
        supportingContent = value?.let { { Text(it) } },
        trailingContent = if (chevron) ({ Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) }) else null,
    )
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    ListItem(
                        modifier = Modifier.selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) }),
                        headlineContent = { Text(label(option)) },
                        leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
