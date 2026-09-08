package com.ninerouter.android.router

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.ninerouter.android.model.InstanceConfig
import com.ninerouter.android.util.AppLogger

/**
 * Termux bridge — single all-in-one entry point.
 *
 * User flow (Android security requirement):
 *   1. Install Termux from F-Droid or the official GitHub release.
 *   2. Open Termux once.
 *   3. Enable allow-external-apps in ~/.termux/termux.properties.
 *   4. Grant this app the RUN_COMMAND permission in Android Settings.
 *   5. Tap the one button in this app — everything else is automatic.
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

    // ── All-in-one: wipe → install → build → start ──────────────────────

    /**
     * Single call: hard wipes everything, installs all prerequisites,
     * builds 9Router from source, generates fresh secrets, starts the server.
     * Returns true if the command was dispatched to Termux.
     */
    fun runAllInOne(context: Context, config: InstanceConfig): Boolean {
        if (!isInstalled(context)) {
            LogStore.error(TAG, "Termux is not installed. Install it from F-Droid or GitHub releases.")
            return false
        }
        if (!hasPermission(context)) {
            LogStore.error(TAG, "Grant 'Run commands in Termux' permission in Android Settings.")
            return false
        }
        val command = buildAllInOneScript(config)
        val intent = Intent(ACTION_RUN).apply {
            component = ComponentName(PACKAGE, SERVICE)
            putExtra(EXTRA_PATH, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(EXTRA_ARGS, arrayOf("-lc", "cat | bash"))
            putExtra(EXTRA_STDIN, command)
            putExtra(EXTRA_WORKDIR, "/data/data/com.termux/files/home")
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_LABEL, "9Router all-in-one")
            putExtra(EXTRA_DESCRIPTION, "Wipe → install → build → start (fresh keys every time)")
        }
        return try {
            context.startService(intent)
            LogStore.info(TAG, "All-in-one command dispatched to Termux")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Could not start Termux RunCommandService", e)
            LogStore.error(TAG, "Termux command failed: ${e.message}")
            false
        }
    }

    // ── Stop ────────────────────────────────────────────────────────────

    fun stop(context: Context, port: Int): Boolean = runCommand(
        context,
        "sv down 9router 2>/dev/null || true; " +
            "pkill -f 'standalone/server.js' 2>/dev/null || true; " +
            "pkill -f 'custom-server.js' 2>/dev/null || true"
    )

    fun restart(context: Context, config: InstanceConfig): Boolean {
        stop(context, config.port)
        return runAllInOne(context, config)
    }

    // ── Internal ─────────────────────────────────────────────────────────

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

    /**
     * Builds a single bash script that performs the complete lifecycle:
     *   hard wipe → install packages → clone/build → generate secrets → start
     *
     * Every invocation generates brand-new keys; old keys are destroyed.
     */
    private fun buildAllInOneScript(config: InstanceConfig): String {
        val port = config.port
        val host = config.host
        val bindHost = config.bindHost
        return """
set -Eeuo pipefail

HOME_DIR="$$HOME"
APP_DIR="$$HOME_DIR/.9router"
REPO_DIR="$$APP_DIR/upstream"
LOG="$$APP_DIR/all-in-one.log"
SERVER_LOG="$$APP_DIR/server.log"
PREFIX_DIR="$${PREFIX:-/data/data/com.termux/files/usr}"

mkdir -p "$$APP_DIR"
exec >>"$$LOG" 2>&1

echo "[all-in-one] ===== START $(date -Is) port=$port ====="

# ── Wake lock ──────────────────────────────────────────────────────
termux-wake-lock 2>/dev/null || echo "[all-in-one] wake-lock unavailable"

# ── Sanity: Termux packages ────────────────────────────────────────
command -v pkg >/dev/null 2>&1 || { echo "ERROR: not inside Termux"; exit 20; }
echo "[all-in-one] installing system packages…"
pkg update -y >/dev/null 2>&1 || true
pkg install -y git nodejs-lts python curl openssl-tool termux-services >/dev/null 2>&1 || true
node --version; npm --version; git --version

# ── HARD WIPE: destroy everything ──────────────────────────────────
echo "[all-in-one] HARD WIPE — burning old data, keys, build cache…"
rm -rf "$$APP_DIR/data"
rm -rf "$$APP_DIR/db"
rm -rf "$$APP_DIR/backup"
rm -rf "$$APP_DIR/auth"
rm -rf "$$APP_DIR/logs"
rm -rf "$$APP_DIR/.env"
rm -rf "$$APP_DIR/jwt-secret"
rm -rf "$$APP_DIR/data.sqlite"
rm -rf "$$APP_DIR/machine-id"
rm -rf "$$REPO_DIR/.next"
mkdir -p "$$APP_DIR/data" "$$APP_DIR/db" "$$APP_DIR/auth" "$$APP_DIR/backup" "$$APP_DIR/logs"
echo "[all-in-one] old data burned ✓"

# ── Clone / update source ──────────────────────────────────────────
if [ -d "$$REPO_DIR/.git" ]; then
  cd "$$REPO_DIR"
  git fetch origin 2>/dev/null || true
  git reset --hard "origin/source" 2>/dev/null || git pull origin source 2>/dev/null || true
  echo "[all-in-one] source updated ✓"
else
  rm -rf "$$REPO_DIR"
  echo "[all-in-one] cloning9router…"
  git clone --branch source --depth 1 https://github.com/9router/9router.git "$$REPO_DIR" 2>/dev/null || \
  git clone --branch main --depth 1 https://github.com/9router/9router.git "$$REPO_DIR"
  echo "[all-in-one] clone done ✓"
fi

# ── npm install + build standalone ─────────────────────────────────
cd "$$REPO_DIR"
if [ ! -f "$$REPO_DIR/.next/standalone/server.js" ] || [ ! -d "$$REPO_DIR/node_modules" ]; then
  echo "[all-in-one] installing npm deps…"
  npm install --no-audit --no-fund --loglevel=error 2>&1 | tail -5
  echo "[all-in-one] building Next.js standalone…"
  npm run build 2>&1 | tail -5
  mkdir -p .next/standalone
  cp -r public .next/standalone/ 2>/dev/null || true
  cp -r .next/static .next/standalone/.next/ 2>/dev/null || true
  echo "[all-in-one] build done ✓"
else
  echo "[all-in-one] existing build OK"
fi

# ── Generate fresh secrets ─────────────────────────────────────────
echo "[all-in-one] generating fresh secrets…"
JWT=$$(node -e 'console.log(require("crypto").randomBytes(32).toString("hex"))')
PASS=$$(node -e 'console.log(require("crypto").randomBytes(16).toString("hex"))')
API=$$(node -e 'console.log(require("crypto").randomBytes(32).toString("hex"))')
SALT=$$(node -e 'console.log(require("crypto").randomBytes(16).toString("hex"))')

cat > "$$APP_DIR/.env" <<ENVEOF
JWT_SECRET=$$JWT
INITIAL_PASSWORD=$$PASS
API_KEY_SECRET=$$API
MACHINE_ID_SALT=$$SALT
DATA_DIR=$$APP_DIR/data
PORT=$port
HOSTNAME=$bindHost
NODE_ENV=production
BASE_URL=http://$$host:$port
NEXT_PUBLIC_BASE_URL=http://$$host:$port
AUTH_COOKIE_SECURE=false
REQUIRE_API_KEY=false
ENABLE_REQUEST_LOGS=false
OBSERVABILITY_ENABLED=true
ENVEOF
chmod 600 "$$APP_DIR/.env"
echo "[all-in-one] secrets written ✓"

# ── Register as termux-services daemon ──────────────────────────────
SVC_DIR="$$PREFIX_DIR/var/service/9router"
mkdir -p "$$SVC_DIR/log" "$$APP_DIR/svlog"
cat > "$$SVC_DIR/run" <<SVC
#!$$PREFIX_DIR/bin/sh
cd "$$REPO_DIR"
set -a
. "$$APP_DIR/.env"
set +a
exec node .next/standalone/server.js 2>&1
SVC
chmod +x "$$SVC_DIR/run"
cat > "$$SVC_DIR/log/run" <<SVCLOG
#!$$PREFIX_DIR/bin/sh
exec svlogd -tt "$$APP_DIR/svlog"
SVCLOG
chmod +x "$$SVC_DIR/log/run"

# ── Auto-start on boot ─────────────────────────────────────────────
mkdir -p "$$HOME_DIR/.termux/boot"
cat > "$$HOME_DIR/.termux/boot/start-9router" <<BOOT
#!$$PREFIX_DIR/bin/sh
termux-wake-lock 2>/dev/null || true
sv-enable 9router 2>/dev/null || true
sv up 9router 2>/dev/null || true
BOOT
chmod +x "$$HOME_DIR/.termux/boot/start-9router"

# ── Start server ───────────────────────────────────────────────────
echo "[all-in-one] starting server on port $port…"
sv down 9router 2>/dev/null || true
pkill -f 'standalone/server.js' 2>/dev/null || true
sleep 1
sv-enable 9router 2>/dev/null || true
sv up 9router 2>/dev/null || true
sleep 4

# ── Health check ───────────────────────────────────────────────────
if curl -fsS -o /dev/null "http://127.0.0.1:$port/" 2>/dev/null; then
  echo "[all-in-one] ✓ Server is live on $$host:$port"
else
  echo "[all-in-one] sv may need more time, starting fallback node…"
  cd "$$REPO_DIR"
  set -a; . "$$APP_DIR/.env"; set +a
  export PORT=$port HOSTNAME=$bindHost
  nohup node .next/standalone/server.js > "$$SERVER_LOG" 2>&1 &
  sleep 5
  if curl -fsS -o /dev/null "http://127.0.0.1:$port/" 2>/dev/null; then
    echo "[all-in-one] ✓ Fallback node live on $$host:$port"
  else
    echo "[all-in-one] ⚠ Server starting… check log: $$SERVER_LOG"
  fi
fi

echo "[all-in-one] ===== COMPLETE $(date -Is) ====="
echo "[all-in-one] Dashboard: http://$$host:$port/dashboard"
echo "[all-in-one] Password: (from .env INITIAL_PASSWORD)"
        """.trimIndent()
    }
}
