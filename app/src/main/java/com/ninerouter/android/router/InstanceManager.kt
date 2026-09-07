package com.ninerouter.android.router

import android.content.Context
import com.ninerouter.android.model.InstanceConfig
import com.ninerouter.android.security.SecureStorage
import com.ninerouter.android.util.AppLogger
import com.ninerouter.android.util.NetworkUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns the application-private data directories and the per-instance 9Router
 * configuration. Each instance is a fresh, isolated directory plus freshly
 * generated secrets (via [SecureStorage]); nothing is reused between reinstalls
 * and secrets are never hard-coded.
 */
object InstanceManager {

    private const val TAG = "InstanceManager"
    private const val PREFS = "instance_prefs"
    private const val KEY_CURRENT_INSTANCE = "current_instance_id"

    private const val DEFAULT_PORT = NetworkUtils.DEFAULT_PORT
    private const val DEFAULT_HOST = NetworkUtils.DEFAULT_HOST

    fun root(context: Context): File = File(context.filesDir, "9router")

    fun dataRoot(context: Context): File = File(context.filesDir, "9router/data")

    /** The directory used by the *current* instance (working dir + its data). */
    fun instanceDir(context: Context): File {
        val id = currentInstanceId(context)
        return if (id != null) File(dataRoot(context), id) else newCurrentInstance(context)
    }

    /** Create a brand new instance directory and remember it as current. */
    fun newCurrentInstance(context: Context): File {
        val id = "inst-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = File(dataRoot(context), id)
        if (!dir.exists()) dir.mkdirs()
        setCurrentInstanceId(context, id)
        val data = File(dir, "data")
        data.mkdirs()
        AppLogger.i(TAG, "New instance dir: ${dir.absolutePath}")
        return dir
    }

    fun currentInstanceId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CURRENT_INSTANCE, null)

    private fun setCurrentInstanceId(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CURRENT_INSTANCE, id).apply()
    }

    /**
     * Build the [InstanceConfig] for a requested host/port. Reads secrets from
     * [SecureStorage] and writes the `.env` into the instance dir.
     */
    fun buildConfig(context: Context, host: String, port: Int, lanMode: Boolean): InstanceConfig {
        val secure = SecureStorage.get(context)
        val instanceDir = instanceDir(context)
        val bundleDir = RouterRuntime.bundleDir(context)
        val nodeExe = RouterRuntime.nodeExecutable(context)

        val displayHost = if (lanMode) {
            // Prefer a real LAN IP so the dashboard links work from other devices.
            NetworkUtils.localIpv4Address(context) ?: host
        } else DEFAULT_HOST
        val bindHost = if (lanMode) "0.0.0.0" else DEFAULT_HOST

        val baseUrl = NetworkUtils.apiBaseUrl(displayHost, port)
        val dashUrl = NetworkUtils.dashboardUrl(displayHost, port)
        val apiKey = secure.endpointApiKey()
        val requireApiKey = false // see settings; kept false by default (matches upstream example)

        val dataDir = File(instanceDir, "data").apply { mkdirs() }
        val logDir = File(instanceDir, "logs").apply { mkdirs() }

        val env = buildMap {
            // Secret wiring — generated fresh per instance.
            put("JWT_SECRET", secure.jwtSecret())
            put("INITIAL_PASSWORD", secure.initialPassword())
            put("API_KEY_SECRET", secure.apiKeySecret())
            put("MACHINE_ID_SALT", secure.machineIdSalt())
            put("DATA_DIR", dataDir.absolutePath)

            // Runtime / networking.
            put("PORT", port.toString())
            put("HOSTNAME", bindHost)
            put("NODE_ENV", "production")
            put("NEXT_DIST_DIR", ".next")
            put("BASE_URL", baseUrl)
            put("NEXT_PUBLIC_BASE_URL", baseUrl)
            put("CLOUD_URL", "https://9router.com")
            put("NEXT_PUBLIC_CLOUD_URL", "https://9router.com")

            // Security / observability defaults.
            put("AUTH_COOKIE_SECURE", "false")
            put("ENABLE_REQUEST_LOGS", "false")
            put("OBSERVABILITY_ENABLED", "true")
            put("REQUIRE_API_KEY", if (requireApiKey) "true" else "false")

            // Android environment overrides: tell Node where ndk/root/home are.
            put("ANDROID_ROOT", System.getenv("ANDROID_ROOT") ?: "/system")
            put("HOME", instanceDir.absolutePath)
            put("TMPDIR", instanceDir.absolutePath)
            // Node uses LD_LIBRARY_PATH for its shared libs on Android.
            val libDir = RouterRuntime.libDir(context)
            put(
                "LD_LIBRARY_PATH",
                buildString {
                    append(libDir.absolutePath)
                    System.getenv("LD_LIBRARY_PATH")?.let { append(":").append(it) }
                }
            )
        }

        writeEnv(instanceDir, env)

        return InstanceConfig(
            // `host` is the client-facing host (127.0.0.1 or the LAN IP in LAN mode).
            // `bindHost` is what the node process actually binds (127.0.0.1 or 0.0.0.0).
            host = displayHost,
            bindHost = bindHost,
            port = port,
            apiBaseUrl = baseUrl,
            dashboardUrl = dashUrl,
            instanceDir = instanceDir,
            bundleDir = bundleDir,
            nodeExecutable = nodeExe,
            env = env,
            apiKey = apiKey,
            requireApiKey = requireApiKey,
        )
    }

    /** Write the `.env` file used by the server process. */
    private fun writeEnv(instanceDir: File, env: Map<String, String>) {
        val sb = StringBuilder()
        for ((k, v) in env) {
            // Quote values with special characters conservatively; never log the content.
            val safeVal = v.replace("\\", "\\\\").replace("\"", "\\\"")
            sb.append("$k=\"$safeVal\"\n")
        }
        File(instanceDir, ".env").writeText(sb.toString(), Charsets.UTF_8)
    }

    /** Remove all application-owned runtime data (instances + extracted runtime). */
    fun clearRuntimeData(context: Context) {
        val root = root(context)
        if (root.exists()) root.deleteRecursively()
        dataRoot(context).mkdirs()
        AppLogger.i(TAG, "Cleared application-owned runtime data")
    }

    const val DEFAULT_PORT_VALUE = DEFAULT_PORT
    const val DEFAULT_HOST_VALUE = DEFAULT_HOST
}
