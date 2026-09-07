package com.ninerouter.android.router

import com.ninerouter.android.model.InstanceConfig
import com.ninerouter.android.model.LogLevel
import com.ninerouter.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Low-level wrapper around the spawned Node.js process.
 *
 * Responsibilities:
 *  - launch `node custom-server.js` from the 9Router standalone bundle,
 *  - stream its merged stdout/stderr into [LogStore] (sanitized) and Logcat,
 *  - best-effort request counting (scans for OpenAI route calls),
 *  - graceful shutdown (SIGTERM then SIGKILL) and exit notification.
 *
 * This class does *not* implement health checks or restart policy — that is
 * [RouterManager]'s job. It only manages a single process.
 */
class RouterProcess(
    private val config: InstanceConfig,
    private val requestLogEnabled: Boolean,
    private val onExit: (exitCode: Int) -> Unit,
    private val onRequestDetected: () -> Unit,
) {

    private val logTag = "RouterProc"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var process: Process? = null
    @Volatile private var running = false
    @Volatile private var started = false
    private var readerJob: Job? = null

    val isAlive: Boolean get() = started && running && (process?.isAlive == true)
    // Android's Process facade does not expose Java 9's Process.pid() on all
    // supported API levels; keep lifecycle management independent of a PID.
    private fun processLabel(p: Process): String = p.toString()

    fun start(): Boolean {
        if (isAlive) return true
        val node = config.nodeExecutable
        if (!node.exists()) {
            LogStore.error(
                logTag,
                "Android Node.js runtime is missing. Expected ${node.absolutePath}. " +
                    "Run scripts/package_runtime.sh with an aarch64-linux-android Node binary."
            )
            return false
        }
        if (!node.canExecute()) {
            node.setExecutable(true, true)
        }
        if (!File(config.bundleDir, "custom-server.js").exists()) {
            LogStore.error(
                logTag,
                "9Router standalone bundle is missing. Expected ${File(config.bundleDir, "custom-server.js").absolutePath}. " +
                    "Run scripts/package_runtime.sh before building the APK."
            )
            return false
        }

        val command = listOf(node.absolutePath, "custom-server.js", "--port", config.port.toString())
        val pb = ProcessBuilder(command)
        pb.directory(config.bundleDir)
        pb.redirectErrorStream(true)

        // Merge our computed env with a minimal safe inherited set.
        val env = pb.environment()
        env.clear()
        env.putAll(config.env)
        // PATH is used by Node to find helpers; include a sane default.
        env.putIfAbsent("PATH", System.getenv("PATH") ?: "/system/bin:/usr/bin:/bin")

        return try {
            val p = pb.start()
            process = p
            running = true
            started = true
            LogStore.info(logTag, "9Router process started (${processLabel(p)})")

            readerJob = scope.launch(Dispatchers.IO) {
                try {
                    p.inputStream.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            if (line.isBlank()) continue
                            handleOutputLine(line)
                        }
                    }
                } catch (e: Exception) {
                    if (running) AppLogger.d(logTag, "Output stream ended: ${e.message}")
                } finally {
                    // Stream closed == process ended.
                }
            }

            scope.launch(Dispatchers.IO) {
                val code = p.waitFor()
                running = false
                readerJob?.cancel()
                LogStore.info(logTag, "9Router process exited with code $code")
                onExit(code)
            }
            true
        } catch (e: Exception) {
            LogStore.error(logTag, "Failed to start 9Router: ${e.message}")
            AppLogger.e(logTag, "Failed to start 9Router", e)
            false
        }
    }

    private fun handleOutputLine(line: String) {
        val level = classify(line)
        LogStore.add(level, "node", line)
        AppLogger.i(logTag, line)

        if (line.contains("EADDRINUSE", ignoreCase = true) ||
            line.contains("address already in use", ignoreCase = true) ||
            line.contains("port is already in use", ignoreCase = true)
        ) {
            LogStore.error(logTag, "Port conflict detected: ${config.port}")
        }
        if (requestLogEnabled || line.contains("/v1", ignoreCase = true) ||
            line.contains("/api/v1", ignoreCase = true) ||
            line.contains("responses", ignoreCase = true)
        ) {
            // Best-effort. Only count things that look like proxied requests.
            if (line.contains("POST", ignoreCase = true) ||
                line.contains("GET /v1", ignoreCase = true)
            ) {
                onRequestDetected()
            }
        }
    }

    private fun classify(line: String): LogLevel {
        val l = line.lowercase()
        return when {
            l.contains("error") || l.contains("fatal") || l.contains("unhandled") -> LogLevel.ERROR
            l.contains("warn") || l.contains("deprecat") -> LogLevel.WARN
            l.contains("ready") || l.contains("local:") || l.contains("listening") ||
                l.contains("start") || l.contains("routing") -> LogLevel.INFO
            else -> LogLevel.DEBUG
        }
    }

    /** Gracefully stop: SIGTERM, then SIGKILL after [graceMs]. */
    fun stop(graceMs: Long = 3500) {
        val p = process ?: return
        if (!p.isAlive) {
            running = false
            return
        }
        running = false
        LogStore.info(logTag, "Stopping 9Router process (${processLabel(p)})…")
        scope.launch(Dispatchers.IO) {
            try {
                p.destroy() // SIGTERM
                val exited = p.waitFor(graceMs, TimeUnit.MILLISECONDS)
                if (!exited) {
                    p.destroyForcibly() // SIGKILL
                }
            } catch (e: Exception) {
                AppLogger.w(logTag, "Error while stopping process: ${e.message}")
            }
        }
    }

    fun kill() {
        process?.destroyForcibly()
    }

    fun release() {
        running = false
        process = null
    }

    /** Cancel the reader/waiter jobs (called when manager disposes of this instance). */
    fun close() {
        kill()
        readerJob?.cancel()
        scope.cancel()
    }
}
