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
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Refresh
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
            onInstallPrerequisites = { vm.installPrerequisites() },
            onConnect = { vm.connect() },
            onDisconnect = { vm.disconnect() },
            onReinstall = { vm.reinstall() },
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
    onInstallPrerequisites: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReinstall: () -> Unit,
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

        // 1. Install Prerequisites Button
        Button(
            onClick = onInstallPrerequisites,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Outlined.CloudDownload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("نصب پیش‌نیازها (INSTALL PREREQUISITES)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(10.dp))

        // 2. Connect / Disconnect Action
        if (state.isRunning) {
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("قطع اتصال (DISCONNECT)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("اتصال و شروع (CONNECT)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))

        // 3. Hard Reinstall — burns old keys & rebuilds fresh (with confirmation)
        var showReinstallConfirm by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = { showReinstallConfirm = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("نصب دوباره سخت — کلیدهای قبلی می‌سوزد (HARD REINSTALL)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (showReinstallConfirm) {
            AlertDialog(
                onDismissRequest = { showReinstallConfirm = false },
                title = { Text("نصب دوباره سخت؟") },
                text = { Text("همه کلیدها، دیتابیس و کش بیلد قبلی کامل پاک می‌شود و همه‌چیز از صفر با کلیدهای جدید ساخته می‌شود. کلیدهای قبلی دیگر کار نمی‌کند. ادامه می‌دهید؟") },
                confirmButton = {
                    TextButton(onClick = { showReinstallConfirm = false; onReinstall() }) { Text("بله، بسوزان و از نو بساز") }
                },
                dismissButton = {
                    TextButton(onClick = { showReinstallConfirm = false }) { Text("انصراف") }
                }
            )
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
