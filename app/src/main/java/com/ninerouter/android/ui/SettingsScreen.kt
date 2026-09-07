package com.ninerouter.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ninerouter.android.model.ConnectionStatus
import com.ninerouter.android.model.Settings
import com.ninerouter.android.ui.components.SectionCard
import com.ninerouter.android.viewmodel.RouterViewModel

@Composable
fun SettingsScreen(
    vm: RouterViewModel,
    onBack: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    var host by remember(settings) { mutableStateOf(settings.host) }
    var port by remember(settings) { mutableStateOf(settings.port.toString()) }
    var lanMode by remember(settings) { mutableStateOf(settings.lanMode) }
    var requireApiKey by remember(settings) { mutableStateOf(settings.requireApiKey) }
    var autoRestart by remember(settings) { mutableStateOf(settings.autoRestart) }
    var maxAttempts by remember(settings) { mutableStateOf(settings.maxRestartAttempts.toString()) }
    var externalBrowser by remember(settings) { mutableStateOf(settings.openDashboardInExternalBrowser) }

    var showLanWarn by remember { mutableStateOf(false) }
    var showReinitConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showApplyHint by remember { mutableStateOf(false) }
    var fatalError by remember { mutableStateOf<String?>(null) }

    fun persist() {
        val newSettings = Settings(
            host = host.ifBlank { "127.0.0.1" },
            port = port.toIntOrNull() ?: 20128,
            lanMode = lanMode,
            requireApiKey = requireApiKey,
            autoRestart = autoRestart,
            maxRestartAttempts = maxAttempts.toIntOrNull() ?: 5,
            requestLogEnabled = settings.requestLogEnabled,
            openDashboardInExternalBrowser = externalBrowser,
        )
        vm.saveSettings(newSettings)
        showApplyHint = true
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Column(Modifier.padding(16.dp).verticalScroll(scroll)) {
            SectionCard("Local endpoint") {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host / bind address") },
                    supportingText = { Text("127.0.0.1 = localhost only · 0.0.0.0 = LAN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Switch(checked = lanMode, onCheckedChange = { checked ->
                        if (checked) {
                            lanMode = true
                            showLanWarn = true
                        } else {
                            lanMode = false
                            persist()
                        }
                    })
                    Spacer(Modifier.width(8.dp))
                    Text("Enable LAN access (0.0.0.0)", style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard("Security") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = requireApiKey, onCheckedChange = { requireApiKey = it; persist() })
                    Spacer(Modifier.width(8.dp))
                    Text("Require API key on /v1", style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard("Recovery & runtime") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = autoRestart, onCheckedChange = { autoRestart = it; persist() })
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-restart on crash", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = externalBrowser, onCheckedChange = { externalBrowser = it; persist() })
                    Spacer(Modifier.width(8.dp))
                    Text("Open dashboard in external browser", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxAttempts,
                    onValueChange = { maxAttempts = it.filter(Char::isDigit) },
                    label = { Text("Max restart attempts") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showReinitConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reinitialize Local Instance")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear Application Runtime Data")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenDashboard, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Dashboard to configure providers")
                }
            }

            if (showApplyHint) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Settings saved. Restart (Disconnect → Connect) to apply host/port changes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (fatalError != null) {
                Spacer(Modifier.height(12.dp))
                Text(fatalError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // --- Dialogs ---
    if (showLanWarn) {
        AlertDialog(
            onDismissRequest = { showLanWarn = false; lanMode = settings.lanMode },
            title = { Text("Enable LAN access?") },
            text = {
                Text(
                    "WARNING: Binding to 0.0.0.0 exposes the 9Router API and dashboard to " +
                        "ANY device on your local network. Anyone who can reach this device " +
                        "may be able to use your configured provider credentials.\n\n" +
                        "Only enable this on trusted networks. Keep API-key protection enabled. " +
                        "Never expose it publicly."
                )
            },
            confirmButton = {
                TextButton(onClick = { showLanWarn = false; persist() }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showLanWarn = false; lanMode = false }) { Text("Cancel") }
            },
        )
    }

    if (showReinitConfirm) {
        AlertDialog(
            onDismissRequest = { showReinitConfirm = false },
            title = { Text("Reinitialize Local Instance?") },
            text = {
                Text(
                    "This stops the current instance, creates a fresh data directory, generates " +
                        "new secrets, and starts a clean instance. Provider accounts are unaffected. " +
                        "This is for local-instance recovery/testing and will NOT bypass any provider " +
                        "quota or account restrictions."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReinitConfirm = false
                    vm.reinitialize()
                }) { Text("Reinitialize & Start") }
            },
            dismissButton = {
                TextButton(onClick = { showReinitConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear runtime data?") },
            text = { Text("This deletes all application-owned runtime data (instances and the embedded runtime). It will be re-extracted on next CONNECT.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    vm.clearAllRuntimeData()
                    fatalError = null
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
