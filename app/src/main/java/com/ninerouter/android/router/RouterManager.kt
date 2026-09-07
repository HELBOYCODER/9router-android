package com.ninerouter.android.router

import android.content.Context
import com.ninerouter.android.model.ConnectionStatus
import com.ninerouter.android.model.RouterStatus
import com.ninerouter.android.security.SecureStorage
import com.ninerouter.android.util.AppLogger
import com.ninerouter.android.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.pow

/**
 * Singleton orchestrator for the embedded 9Router instance.
 *
 * Owns the process lifecycle, health verification, automatic crash recovery
 * (exponential backoff, capped), and the observable [RouterStatus] that drives the
 * UI + foreground notification. The foreground service is a thin shell that starts,
 * observes, and stops this manager.
 */
object RouterManager {

    private const val TAG = "RouterManager"
    private const val HEALTH_TIMEOUT_MS = 45_000L
    private const val BASE_BACKOFF_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var appContext: Context? = null

    private val _state = MutableStateFlow(RouterStatus())
    val state: StateFlow<RouterStatus> = _state

    /** Logs are shared with the Logs screen; this exposes [LogStore.entries]. */
    val logs: StateFlow<List<com.ninerouter.android.model.LogEntry>> = LogStore.entries

    @Volatile private var currentProcess: RouterProcess? = null
    @Volatile private var shouldBeRunning = false
    @Volatile private var intentionalStop = false
    @Volatile private var restartAttempts = 0
    @Volatile private var maxRestartAttempts = 5
    @Volatile private var autoRestartEnabled = true

    private var requestCount: Long = 0L
    private var healthJob: Job? = null
    private var restartJob: Job? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    // ---------------------------------------------------------------------------
    // State helpers
    // ---------------------------------------------------------------------------

    private fun patch(transform: RouterStatus.() -> RouterStatus) {
        _state.value = _state.value.transform()
    }

    private fun emit(
        status: ConnectionStatus,
        host: String = _state.value.host,
        port: Int = _state.value.port,
        apiKey: String = _state.value.apiKey,
        startedAt: Long? = _state.value.startedAtMillis,
        error: String? = null,
        instanceId: String? = _state.value.instanceId,
    ) {
        _state.value = RouterStatus(
            status = status,
            host = host,
            port = port,
            apiBaseUrl = NetworkUtils.apiBaseUrl(host, port),
            dashboardUrl = NetworkUtils.dashboardUrl(host, port),
            apiKey = apiKey,
            startedAtMillis = startedAt,
            lastError = error,
            requestCount = requestCount,
            instanceId = instanceId,
            runtimePresent = RouterRuntime.isPresent(appContext!!),
        )
    }

    // ---------------------------------------------------------------------------
    // Public control surface (all non-blocking; they run on the manager scope)
    // ---------------------------------------------------------------------------

    fun start(context: Context, requestedPort: Int? = null) {
        initialize(context)
        scope.launch {
            mutex.withLock {
                if (shouldBeRunning) return@withLock
                intentionalStop = false
                shouldBeRunning = true
                restartAttempts = 0
                val settings = loadSettings()
                autoRestartEnabled = settings.autoRestart
                maxRestartAttempts = settings.maxRestartAttempts
                launchInstance(settings, requestedPort ?: settings.port)
            }
        }
    }

    fun stop() {
        scope.launch {
            mutex.withLock {
                if (!shouldBeRunning && currentProcess == null) {
                    emit(ConnectionStatus.STOPPED)
                    return@withLock
                }
                shouldBeRunning = false
                doStop()
                emit(ConnectionStatus.STOPPED)
                LogStore.info(TAG, "Router stopped by user")
            }
        }
    }

    fun restart() {
        scope.launch {
            mutex.withLock {
                val host = _state.value.host
                val port = _state.value.port
                intentionalStop = true
                shouldBeRunning = false
                doStop()
                LogStore.info(TAG, "Restart requested")
                shouldBeRunning = true
                intentionalStop = false
                restartAttempts = 0
                val settings = loadSettings()
                launchInstance(settings, port)
            }
        }
    }

    /** Reinitialize: fresh data dir, fresh secrets, clean config, then start. */
    fun reinitialize(context: Context) {
        initialize(context)
        scope.launch {
            mutex.withLock {
                intentionalStop = true
                shouldBeRunning = false
                doStop()
                LogStore.clear()
                LogStore.info(TAG, "Reinitializing local instance…")
                SecureStorage.get(context).rotateSecrets()
                InstanceManager.newCurrentInstance(context)
                shouldBeRunning = true
                intentionalStop = false
                restartAttempts = 0
                val settings = loadSettings()
                launchInstance(settings, settings.port)
            }
        }
    }

    /** Wipe all application-owned runtime data (runtime + instances). */
    fun clearRuntimeData(context: Context) {
        initialize(context)
        scope.launch {
            mutex.withLock {
                intentionalStop = true
                shouldBeRunning = false
                doStop()
                LogStore.clear()
                InstanceManager.clearRuntimeData(context)
                emit(ConnectionStatus.STOPPED, error = "Runtime data cleared")
            }
        }
    }

    fun clearLogs() = LogStore.clear()

    suspend fun currentSettings() = loadSettings()

    // ---------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------

    private suspend fun loadSettings() = com.ninerouter.android.data.SettingsRepository.get(appContext!!).settings.first()

    private suspend fun launchInstance(settings: com.ninerouter.android.model.Settings, port: Int) {
        val ctx = appContext ?: return

        if (!RouterRuntime.extractIfNeeded(ctx)) {
            emit(ConnectionStatus.ERROR, error = "Embedded 9Router runtime not found. Run scripts/package_runtime.sh, then rebuild.")
            shouldBeRunning = false
            return
        }

        val lanMode = settings.lanMode && port != 0 && settings.host != "0.0.0.0"
        val config = InstanceManager.buildConfig(ctx, settings.host, port, lanMode)
        val apiKey = config.apiKey

        LogStore.info(TAG, "Starting 9Router on ${config.bindHost}:${config.port}")
        emit(
            status = ConnectionStatus.CONNECTING,
            host = config.host,
            port = config.port,
            apiKey = apiKey,
            error = null,
            instanceId = InstanceManager.currentInstanceId(ctx),
        )

        val process = RouterProcess(
            config = config,
            requestLogEnabled = settings.requestLogEnabled,
            onExit = { code -> handleExit(code) },
            onRequestDetected = {
                requestCount = requestCount + 1
                val newCount = requestCount
                patch { copy(requestCount = newCount) }
            },
        )
        currentProcess = process

        if (!process.start()) {
            emit(ConnectionStatus.ERROR, host = config.bindHost, port = config.port, apiKey = apiKey,
                error = "Failed to start 9Router process", instanceId = InstanceManager.currentInstanceId(ctx))
            shouldBeRunning = false
            return
        }

        // Health verification
        healthJob = scope.launch {
            val health = HealthChecker.waitUntilHealthy(config.apiBaseUrl, apiKey, HEALTH_TIMEOUT_MS)
            if (!shouldBeRunning) return@launch
            if (health.ready || health.healthy) {
                LogStore.info(TAG, "9Router is healthy (HTTP ${health.httpCode}, ${health.latencyMs}ms)")
                emit(
                    status = ConnectionStatus.CONNECTED,
                    host = config.host,
                    port = config.port,
                    apiKey = apiKey,
                    startedAt = System.currentTimeMillis(),
                    error = null,
                    instanceId = InstanceManager.currentInstanceId(ctx),
                )
            } else {
                LogStore.error(TAG, "Health check failed: ${health.error ?: "no response (HTTP ${health.httpCode})"}")
                // The process may still be cranking; give the auto-recovery a chance
                // by letting onExit (on a crash) drive a retry. If it simply can't
                // bind it will exit and onExit triggers backoff. Report state now.
                if (currentProcess?.isAlive != true) {
                    emit(ConnectionStatus.ERROR, host = config.bindHost, port = config.port, apiKey = apiKey,
                        error = "Instance not responding", instanceId = InstanceManager.currentInstanceId(ctx))
                }
            }
        }
    }

    /** Called from the process's onExit. Single decision point for auto-recovery. */
    private fun handleExit(code: Int) {
        healthJob?.cancel()
        if (!shouldBeRunning || intentionalStop) return

        LogStore.warn(TAG, "9Router exited unexpectedly (code $code)")
        if (!autoRestartEnabled) {
            emit(ConnectionStatus.ERROR, error = "9Router exited (code $code)")
            shouldBeRunning = false
            return
        }
        if (restartAttempts >= maxRestartAttempts) {
            emit(ConnectionStatus.ERROR, error = "9Router crashed ${restartAttempts} times; giving up (auto-restart disabled)")
            LogStore.error(TAG, "Max restart attempts reached ($maxRestartAttempts); not restarting.")
            shouldBeRunning = false
            return
        }

        val attempt = restartAttempts + 1
        restartAttempts = attempt
        val backoff = BASE_BACKOFF_MS * (2.0.pow(attempt - 1)).toLong()
        LogStore.warn(TAG, "Restarting in ${backoff}ms (attempt $attempt/$maxRestartAttempts)")
        emit(ConnectionStatus.RESTARTING, error = "Crash detected — restarting (attempt $attempt)")

        restartJob?.cancel()
        restartJob = scope.launch {
            delay(backoff)
            if (!shouldBeRunning || intentionalStop) return@launch
            mutex.withLock {
                if (!shouldBeRunning || intentionalStop) return@withLock
                val settings = loadSettings()
                launchInstance(settings, _state.value.port)
            }
        }
    }

    private fun doStop() {
        restartJob?.cancel()
        healthJob?.cancel()
        val p = currentProcess
        currentProcess = null
        if (p != null) {
            p.stop()
        }
        requestCount = 0L
    }
}
