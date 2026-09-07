package com.ninerouter.android.router

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.ninerouter.android.model.InstanceConfig
import com.ninerouter.android.util.AppLogger

/**
 * Termux bridge for Android devices where Node.js cannot be embedded as a normal
 * executable. Termux owns the Android/Bionic Node runtime and packages.
 *
 * User prerequisites (Android security requirement): install Termux from F-Droid
 * or the official GitHub release, open it once, enable allow-external-apps in
 * ~/.termux/termux.properties, and grant this app the RUN_COMMAND permission in
 * Settings -> Apps -> 9Router -> Additional permissions.
 */
object TermuxIntegration {
    private const val TAG = "Termux"
    const val PACKAGE = "com.termux"
    private const val SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN = "com.termux.RUN_COMMAND"
    private const val PERMISSION_RUN = "com.termux.permission.RUN_COMMAND"
    private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    private const val EXTRA_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION_RUN) == PackageManager.PERMISSION_GRANTED

    fun openInstallPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("https://github.com/termux/termux-app/releases")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Start prerequisite installation, upstream build, and server in Termux. */
    fun start(context: Context, config: InstanceConfig, reinitialize: Boolean = false): Boolean {
        if (!isInstalled(context)) {
            LogStore.error(TAG, "Termux is not installed. Install it from F-Droid or the official GitHub release.")
            return false
        }
        if (!hasPermission(context)) {
            LogStore.error(TAG, "Run commands in Termux permission is not granted. Enable it in Android Settings.")
            return false
        }
        val command = bootstrapScript(config, reinitialize)
        val intent = Intent(ACTION_RUN).apply {
            component = ComponentName(PACKAGE, SERVICE)
            putExtra(EXTRA_PATH, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(EXTRA_ARGS, arrayOf("-lc", "cat | bash"))
            putExtra(EXTRA_STDIN, command)
            putExtra(EXTRA_WORKDIR, "/data/data/com.termux/files/home")
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_LABEL, "9Router bootstrap")
            putExtra(EXTRA_DESCRIPTION, "Install prerequisites, build and run 9Router")
        }
        return try {
            context.startService(intent)
            LogStore.info(TAG, "Bootstrap sent to Termux; checking local health…")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Could not start Termux RunCommandService", e)
            LogStore.error(TAG, "Termux command failed to start: ${e.message}")
            false
        }
    }

    fun stop(context: Context, port: Int): Boolean = runCommand(
        context,
        "pkill -f '9router/.next/standalone/custom-server.js' 2>/dev/null || " +
            "pkill -f 'custom-server.js --port $port' 2>/dev/null || true"
    )

    fun restart(context: Context, config: InstanceConfig): Boolean {
        stop(context, config.port)
        return start(context, config)
    }

    private fun runCommand(context: Context, shell: String): Boolean {
        if (!isInstalled(context) || !hasPermission(context)) return false
        val intent = Intent(ACTION_RUN).apply {
            component = ComponentName(PACKAGE, SERVICE)
            putExtra(EXTRA_PATH, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(EXTRA_ARGS, arrayOf("-lc", shell))
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_LABEL, "9Router control")
        }
        return runCatching { context.startService(intent); true }.getOrElse { false }
    }

    private fun q(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun bootstrapScript(config: InstanceConfig, reinitialize: Boolean): String {
        val port = config.port
        val dollar = '$'
        val reset = if (reinitialize) "rm -rf ${dollar}APP_DIR/data ${dollar}APP_DIR/.env" else ":"
        // Keep the script POSIX/bash compatible with Termux. DOLLAR placeholders
        // avoid Kotlin interpolating shell variables before the intent is sent.
        return """
            set -Eeuo pipefail
            HOME_DIR="__DOLLAR__HOME"
            APP_DIR="__DOLLAR__HOME_DIR/.9router"
            REPO_DIR="__DOLLAR__APP_DIR/upstream"
            LOG="__DOLLAR__APP_DIR/bootstrap.log"
            mkdir -p "__DOLLAR__APP_DIR"
            exec >>"__DOLLAR__LOG" 2>&1
            echo "[9router] bootstrap started __DOLLAR__(date -Is)"
            command -v pkg >/dev/null 2>&1 || { echo "ERROR: Termux pkg not found"; exit 20; }
            pkg update -y
            for p in nodejs-lts git curl; do
              command -v "__DOLLAR__{p%%-*}" >/dev/null 2>&1 || pkg install -y "__DOLLAR__p"
            done
            command -v npm >/dev/null 2>&1 || pkg install -y nodejs-lts
            $reset
            mkdir -p "__DOLLAR__APP_DIR"
            if [ ! -d "__DOLLAR__REPO_DIR/.git" ]; then
              rm -rf "__DOLLAR__REPO_DIR"
              git clone --depth 1 https://github.com/decolua/9router.git "__DOLLAR__REPO_DIR"
            fi
            cd "__DOLLAR__REPO_DIR"
            git fetch --depth 1 origin eb712ca821f0ba6bc41043fbd14494c5af5daba5
            git checkout --force eb712ca821f0ba6bc41043fbd14494c5af5daba5
            npm install --no-audit --no-fund
            npm run build
            mkdir -p "__DOLLAR__APP_DIR/data"
            if [ ! -f "__DOLLAR__APP_DIR/.env" ]; then
              JWT=__DOLLAR__(node -e 'console.log(require("crypto").randomBytes(32).toString("hex"))')
              PASS=__DOLLAR__(node -e 'console.log(require("crypto").randomBytes(16).toString("hex"))')
              API=__DOLLAR__(node -e 'console.log(require("crypto").randomBytes(32).toString("hex"))')
              SALT=__DOLLAR__(node -e 'console.log(require("crypto").randomBytes(16).toString("hex"))')
              cat >"__DOLLAR__APP_DIR/.env" <<ENV
            JWT_SECRET=__DOLLAR__JWT
            INITIAL_PASSWORD=__DOLLAR__PASS
            API_KEY_SECRET=__DOLLAR__API
            MACHINE_ID_SALT=__DOLLAR__SALT
            DATA_DIR=__DOLLAR__APP_DIR/data
            PORT=$port
            HOSTNAME=127.0.0.1
            NODE_ENV=production
            BASE_URL=http://127.0.0.1:$port
            NEXT_PUBLIC_BASE_URL=http://127.0.0.1:$port
            AUTH_COOKIE_SECURE=false
            REQUIRE_API_KEY=false
            ENABLE_REQUEST_LOGS=false
            OBSERVABILITY_ENABLED=true
            ENV
              chmod 600 "__DOLLAR__APP_DIR/.env"
            fi
            pkill -f '9router/.next/standalone/custom-server.js' 2>/dev/null || true
            cd "__DOLLAR__REPO_DIR/.next/standalone"
            set -a; . "__DOLLAR__APP_DIR/.env"; set +a
            nohup node custom-server.js --port $port >"__DOLLAR__APP_DIR/server.log" 2>&1 &
            echo "[9router] server started on 127.0.0.1:$port"
        """.trimIndent().replace("__DOLLAR__", dollar.toString())
    }
}
