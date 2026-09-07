package com.ninerouter.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ninerouter.android.model.ConnectionStatus
import com.ninerouter.android.model.RouterStatus
import com.ninerouter.android.model.Settings
import com.ninerouter.android.ui.components.CopyableField
import com.ninerouter.android.ui.components.NavRow
import com.ninerouter.android.ui.components.SectionCard
import com.ninerouter.android.ui.components.StatusPill
import com.ninerouter.android.ui.components.ValueBox
import com.ninerouter.android.viewmodel.RouterViewModel
import java.util.Locale
import kotlin.math.absoluteValue

private enum class Screen { Main, Logs, Providers, Settings }

@Composable
fun MainScreen(
    vm: RouterViewModel,
    onOpenDashboard: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    val state by vm.state.collectAsState()
    val settings by vm.settings.collectAsState()

    when (screen) {
        Screen.Main -> MainContent(
            state = state,
            settings = settings,
            onConnect = { vm.connect() },
            onDisconnect = { vm.disconnect() },
            onOpenDashboard = onOpenDashboard,
            onOpenLogs = { screen = Screen.Logs },
            onOpenProviders = { screen = Screen.Providers },
            onOpenSettings = { screen = Screen.Settings },
        )
        Screen.Logs -> LogsScreen(logs = vm.logs, onBack = { screen = Screen.Main })
        Screen.Providers -> ProvidersScreen(onBack = { screen = Screen.Main })
        Screen.Settings -> SettingsScreen(
            vm = vm,
            onBack = { screen = Screen.Main },
            onOpenDashboard = onOpenDashboard,
        )
    }
}

@Composable
private fun MainContent(
    state: RouterStatus,
    settings: Settings,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scroll)
    ) {
        Spacer(Modifier.height(12.dp))

        // Header
        Text("9Router", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        StatusPill(state.status)

        Spacer(Modifier.height(20.dp))

        // Main action
        if (state.isRunning) {
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("DISCONNECT", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("CONNECT", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (state.lastError != null && state.status == ConnectionStatus.ERROR) {
            Spacer(Modifier.height(12.dp))
            Text(
                state.lastError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                    .padding(12.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        // Endpoint info
        SectionCard("Local API") {
            CopyableField("API Base URL", state.apiBaseUrl)
            Spacer(Modifier.height(14.dp))
            CopyableField("API Key", if (state.apiKey.isBlank()) "(generated on connect)" else state.apiKey)

            Spacer(Modifier.height(14.dp))
            Row {
                ValueBox("Port", state.port.toString(), Modifier.weight(1f))
                ValueBox("Host", state.host, Modifier.weight(1f))
                ValueBox("Mode", if (settings.lanMode) "LAN" else "Localhost", Modifier.weight(1f))
            }

            if (state.isRunning) {
                Spacer(Modifier.height(14.dp))
                Row {
                    ValueBox("Uptime", formatUptime(state.uptimeMillis), Modifier.weight(1f))
                    ValueBox("Requests", state.requestCount.toString(), Modifier.weight(1f))
                    ValueBox("Status", state.status.name, Modifier.weight(1f))
                }
            }

            if (state.isRunning) {
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onOpenDashboard,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("OPEN DASHBOARD")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Navigation
        SectionCard("") {
            NavRow(Icons.Outlined.Dashboard, "Dashboard", onOpenDashboard)
            HorizontalDivider()
            NavRow(Icons.Outlined.List, "Logs", onOpenLogs)
            HorizontalDivider()
            NavRow(Icons.Outlined.Cloud, "Providers", onOpenProviders)
            HorizontalDivider()
            NavRow(Icons.Outlined.Tune, "Settings", onOpenSettings)
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun formatUptime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}
