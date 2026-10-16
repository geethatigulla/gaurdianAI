package ai.guardian.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Synthetic examples for demos — written for this project, not real messages. */
private val textExamples = listOf(
    "KYC phishing" to "Dear customer, your SBI account will be blocked today. Update your KYC immediately: http://sbi-kyc-update.xyz/login",
    "Fake refund (UPI PIN)" to "Congratulations! You have won Rs 5000 cashback. Enter your UPI PIN to receive the money in your account.",
    "Digital arrest" to "This is CBI officer. A parcel with illegal drugs is registered in your name. You are under digital arrest. Do not disconnect.",
    "Genuine OTP" to "482913 is your OTP for login to HDFC Bank NetBanking. Valid for 5 minutes. Do not share this OTP with anyone.",
)
private val urlExamples = listOf(
    "Look-alike bank" to "http://hdfcbnak.com/login",
    "Hidden destination (@)" to "https://sbi.co.in@secure-verify.top/kyc",
    "APK download" to "http://45.67.89.10/EB-bill.apk",
    "Official site" to "https://www.onlinesbi.sbi",
)
private val phoneExamples = listOf(
    "Indian mobile" to "+91 98765 43210",
    "Foreign number" to "+92 300 1234567",
    "Invalid number" to "+91 12345",
    "Business sender ID" to "VM-SBIINB",
)
private val addressExamples = listOf(
    "Look-alike domain" to "hdfcbnak.com",
    "Home router (private IP)" to "192.168.1.1",
    "Public IP" to "8.8.8.8",
    "Official domain" to "onlinesbi.sbi",
)

private fun examplesFor(mode: InputMode) = when (mode) {
    InputMode.TEXT -> textExamples
    InputMode.URL -> urlExamples
    InputMode.PHONE -> phoneExamples
    InputMode.ADDRESS -> addressExamples
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeInputScreen(
    onBack: () -> Unit,
    onResult: (String) -> Unit,
    viewModel: AnalyzeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var examplesOpen by remember { mutableStateOf(false) }
    val singleLine = state.mode.singleLine

    LaunchedEffect(Unit) { viewModel.results.collect(onResult) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.mode.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    Box {
                        DropdownMenu(expanded = examplesOpen, onDismissRequest = { examplesOpen = false }) {
                            examplesFor(state.mode).forEach { (label, sample) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    viewModel.onInputChange(sample)
                                    examplesOpen = false
                                })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = if (singleLine) 56.dp else 180.dp),
                placeholder = { Text(state.mode.placeholder) },
                singleLine = singleLine,
                isError = state.tooLong,
                supportingText = if (state.tooLong) ({ Text("Too long — max ${AnalyzeUiState.MAX_INPUT} characters") }) else null,
                keyboardActions = KeyboardActions(onGo = { viewModel.analyze() }),
                keyboardOptions = when (state.mode) {
                    InputMode.TEXT -> KeyboardOptions.Default
                    InputMode.PHONE -> KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Go)
                    InputMode.URL, InputMode.ADDRESS -> KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go)
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { clipboard.getText()?.text?.let(viewModel::onInputChange) }) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Paste")
                }
                OutlinedButton(onClick = { examplesOpen = true }) {
                    Icon(Icons.Outlined.Science, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Try example")
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(
                onClick = viewModel::analyze,
                enabled = state.canAnalyze,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.analyzing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Check")
            }
        }
    }
}
