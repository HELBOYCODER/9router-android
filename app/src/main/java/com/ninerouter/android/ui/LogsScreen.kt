package com.ninerouter.android.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ninerouter.android.model.LogEntry
import com.ninerouter.android.model.LogLevel
import com.ninerouter.android.router.LogStore
import kotlinx.coroutines.flow.StateFlow

@Composable
fun LogsScreen(
    logs: StateFlow<List<LogEntry>>,
    onBack: () -> Unit,
) {
    val entries by logs.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var filter by remember { mutableStateOf<LogLevel?>(null) }
    val shown = if (filter == null) entries else entries.filter { it.level == filter }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Logs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))

            IconButton(onClick = { clipboard.setText(AnnotatedString(LogStore.exportText())) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy logs")
            }
            IconButton(onClick = { exportLogs(context) }) {
                Icon(Icons.Outlined.FileDownload, contentDescription = "Export logs")
            }
            IconButton(onClick = { LogStore.clear() }) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear logs")
            }
        }

        // Level filter chips
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
            FilterChip(selected = filter == LogLevel.INFO, onClick = { filter = LogLevel.INFO }, label = { Text("Info") })
            FilterChip(selected = filter == LogLevel.WARN, onClick = { filter = LogLevel.WARN }, label = { Text("Warn") })
            FilterChip(selected = filter == LogLevel.ERROR, onClick = { filter = LogLevel.ERROR }, label = { Text("Error") })
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No logs yet. Start 9Router to see startup/shutdown/health events.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                items(shown, key = { it.id }) { entry ->
                    LogRow(entry)
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("[${entry.level.name}] ", color = color, style = MaterialTheme.typography.labelMedium)
            Text(entry.tag, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(
                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(entry.timestamp)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        SelectionContainer {
            Text(entry.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun exportLogs(context: Context) {
    try {
        val text = LogStore.exportText()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "9Router logs")
        }
        context.startActivity(Intent.createChooser(send, "Export logs"))
    } catch (e: Exception) {
        // sanitize, never log secrets
        LogStore.error("LogsScreen", "Could not export logs: ${e.message}")
    }
}
