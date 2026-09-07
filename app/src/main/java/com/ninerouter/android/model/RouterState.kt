package com.ninerouter.android.model

import java.io.File

/** Lifecycle status of the embedded 9Router instance. */
enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, RESTARTING, ERROR, STOPPED }

/**
 * Immutable snapshot of the running (or idle) 9Router instance, surfaced to the UI
 * and to the foreground-service notification.
 */
data class RouterStatus(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val host: String = "127.0.0.1",
    val port: Int = 20128,
    val apiBaseUrl: String = "http://127.0.0.1:20128/v1",
    val dashboardUrl: String = "http://127.0.0.1:20128/dashboard",
    val apiKey: String = "",
    val startedAtMillis: Long? = null,
    val lastError: String? = null,
    val requestCount: Long = 0L,
    val instanceId: String? = null,
    val runtimePresent: Boolean = false,
) {
    val uptimeMillis: Long
        get() = startedAtMillis?.let { (System.currentTimeMillis() - it).coerceAtLeast(0) } ?: 0L

    val isRunning: Boolean
        get() = status == ConnectionStatus.CONNECTED ||
            status == ConnectionStatus.CONNECTING ||
            status == ConnectionStatus.RESTARTING
}

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)

/** Everything needed to launch one 9Router process. */
data class InstanceConfig(
    val host: String,             // display / client-facing host (127.0.0.1 or LAN IP)
    val bindHost: String,         // HOSTNAME the node process binds (127.0.0.1 or 0.0.0.0)
    val port: Int,
    val apiBaseUrl: String,
    val dashboardUrl: String,
    val instanceDir: File,
    val bundleDir: File,
    val nodeExecutable: File,
    val env: Map<String, String>,
    val apiKey: String,
    val requireApiKey: Boolean,
)
