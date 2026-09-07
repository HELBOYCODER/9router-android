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
        "sv down 9router 2>/dev/null || true; " +
            "pkill -f 'standalone/server.js' 2>/dev/null || true; " +
            "pkill -f 'custom-server.js' 2>/dev/null || true"
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

    // The bootstrap script is built line-by-line so the Kotlin compiler treats
    // any '$' as a literal character (bash variable). Avoid using Kotlin
    // triple-quoted strings combined with shell variables — they collide and
    // silently break the script.
    private fun bootstrapScript(config: InstanceConfig, reinitialize: Boolean): String {
        val port = config.port
        val reset = if (reinitialize) "rm -rf \$APP_DIR/data \$APP_DIR/.env \$REPO_DIR/.next" else ":"
        val lines = listOf(
            "set -Eeuo pipefail",
            "HOME_DIR=\"\$HOME\"",
            "APP_DIR=\"\$HOME_DIR/.9router\"",
            "REPO_DIR=\"\$APP_DIR/upstream\"",
            "LOG=\"\$APP_DIR/bootstrap.log\"",
            "SERVER_LOG=\"\$APP_DIR/server.log\"",
            "PREFIX_DIR=\"\${PREFIX:-/data/data/com.termux/files/usr}\"",
            "mkdir -p \"\$APP_DIR\"",
            "exec >>\"\$LOG\" 2>&1",
            "echo \"[9router] bootstrap started \$(date -Is) on port $port\"",
            "termux-wake-lock 2>/dev/null || echo \"[9router] wake-lock not available\"",
            "command -v pkg >/dev/null 2>&1 || { echo \"ERROR: Termux pkg not found — open Termux once\"; exit 20; }",
            "pkg update -y >/dev/null 2>&1 || true",
            "echo \"[9router] installing system prerequisites\"",
            "pkg install -y git nodejs-lts python rust make curl openssl-tool termux-services termux-api >/dev/null 2>&1 || true",
            "node --version; npm --version; git --version",
            reset,
            "mkdir -p \"\$APP_DIR\"",
            "if [ ! -d \"\$REPO_DIR/.git\" ]; then",
            "  rm -rf \"\$REPO_DIR\"",
            "  echo \"[9router] cloning upstream\"",
            "  git clone --depth 1 --quiet https://github.com/decolua/9router.git \"\$REPO_DIR\" || { echo \"[9router] clone failed\"; exit 22; }",
            "fi",
            "cd \"\$REPO_DIR\"",
            "if [ ! -f \"\$REPO_DIR/.next/standalone/server.js\" ]; then",
            "  echo \"[9router] installing npm dependencies\"",
            "  npm install --no-audit --no-fund --loglevel=error || { echo \"[9router] npm install failed\"; exit 24; }",
            "  echo \"[9router] building Next.js standalone\"",
            "  npm run build || { echo \"[9router] build failed\"; exit 25; }",
            "  cp -r public .next/standalone/ 2>/dev/null || true",
            "  cp -r .next/static .next/standalone/.next/ 2>/dev/null || true",
            "else",
            "  echo \"[9router] existing standalone build detected\"",
            "fi",
            "mkdir -p \"\$APP_DIR/data\"",
            "if [ ! -f \"\$APP_DIR/.env\" ]; then",
            "  echo \"[9router] generating local secrets\"",
            "  JWT=\$(node -e 'console.log(require(\"crypto\").randomBytes(32).toString(\"hex\"))')",
            "  PASS=\$(node -e 'console.log(require(\"crypto\").randomBytes(16).toString(\"hex\"))')",
            "  API=\$(node -e 'console.log(require(\"crypto\").randomBytes(32).toString(\"hex\"))')",
            "  SALT=\$(node -e 'console.log(require(\"crypto\").randomBytes(16).toString(\"hex\"))')",
            "  cat > \"\$APP_DIR/.env\" <<ENV",
            "JWT_SECRET=\$JWT",
            "INITIAL_PASSWORD=\$PASS",
            "API_KEY_SECRET=\$API",
            "MACHINE_ID_SALT=\$SALT",
            "DATA_DIR=\$APP_DIR/data",
            "PORT=$port",
            "HOSTNAME=${config.bindHost}",
            "NODE_ENV=production",
            "BASE_URL=http://${config.host}:$port",
            "NEXT_PUBLIC_BASE_URL=http://${config.host}:$port",
            "AUTH_COOKIE_SECURE=false",
            "REQUIRE_API_KEY=false",
            "ENABLE_REQUEST_LOGS=false",
            "OBSERVABILITY_ENABLED=true",
            "ENV",
            "  chmod 600 \"\$APP_DIR/.env\"",
            "  echo \"[9router] secrets written to \$APP_DIR/.env\"",
            "else",
            "  echo \"[9router] reusing existing \$APP_DIR/.env\"",
            "  sed -i \"s/^PORT=.*/PORT=$port/\" \"\$APP_DIR/.env\" || true",
            "  sed -i \"s/^HOSTNAME=.*/HOSTNAME=${config.bindHost}/\" \"\$APP_DIR/.env\" || true",
            "  sed -i \"s|^BASE_URL=.*|BASE_URL=http://${config.host}:$port|\" \"\$APP_DIR/.env\" || true",
            "  sed -i \"s|^NEXT_PUBLIC_BASE_URL=.*|NEXT_PUBLIC_BASE_URL=http://${config.host}:$port|\" \"\$APP_DIR/.env\" || true",
            "fi",
            "SVC_DIR=\"\$PREFIX_DIR/var/service/9router\"",
            "mkdir -p \"\$SVC_DIR/log\" \"\$APP_DIR/svlog\"",
            "cat > \"\$SVC_DIR/run\" <<SVC",
            "#!\"\$PREFIX_DIR\"/bin/sh",
            "cd \"\$REPO_DIR\"",
            "set -a",
            ". \"\$APP_DIR/.env\"",
            "set +a",
            "exec node .next/standalone/server.js 2>&1",
            "SVC",
            "chmod +x \"\$SVC_DIR/run\"",
            "cat > \"\$SVC_DIR/log/run\" <<SVCLOG",
            "#!\"\$PREFIX_DIR\"/bin/sh",
            "exec svlogd -tt \"\$APP_DIR/svlog\"",
            "SVCLOG",
            "chmod +x \"\$SVC_DIR/log/run\"",
            "mkdir -p \"\$HOME_DIR/.termux/boot\"",
            "cat > \"\$HOME_DIR/.termux/boot/start-9router\" <<BOOT",
            "#!\"\$PREFIX_DIR\"/bin/sh",
            "termux-wake-lock 2>/dev/null || true",
            "sv-enable 9router 2>/dev/null || true",
            "sv up 9router 2>/dev/null || true",
            "BOOT",
            "chmod +x \"\$HOME_DIR/.termux/boot/start-9router\"",
            "sv down 9router 2>/dev/null || true",
            "pkill -f 'standalone/server.js' 2>/dev/null || true",
            "pkill -f 'custom-server.js' 2>/dev/null || true",
            "sleep 1",
            "sv-enable 9router 2>/dev/null || true",
            "sv up 9router 2>/dev/null || true",
            "sleep 3",
            "if ! curl -fsS -o /dev/null \"http://127.0.0.1:$port/\" 2>/dev/null; then",
            "  echo \"[9router] sv up pending, running background node process\"",
            "  cd \"\$REPO_DIR\"",
            "  set -a; . \"\$APP_DIR/.env\"; set +a",
            "  export PORT=$port",
            "  export HOSTNAME=${config.bindHost}",
            "  nohup node .next/standalone/server.js > \"\$SERVER_LOG\" 2>&1 &",
            "fi",
            "sleep 2",
            "if killall -0 node 2>/dev/null || pgrep -f 'server.js' >/dev/null; then",
            "  echo \"[9router] server active on ${config.host}:$port\"",
            "else",
            "  echo \"[9router] server process check failed, server.log:\"",
            "  tail -n 80 \"\$SERVER_LOG\" 2>&1 || true",
            "fi",
        )
        return lines.joinToString("\n")
    }
}
