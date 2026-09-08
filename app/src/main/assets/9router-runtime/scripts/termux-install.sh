#!/usr/bin/env bash
# ============================================================================
#  9router-android / termux-install.sh
#  Standalone one-command installer for 9Router on Android via Termux.
#  Based on the proven hermes-termux bootstrap pattern.
# ============================================================================

set -euo pipefail

PREFIX_DIR="${PREFIX:-/data/data/com.termux/files/usr}"
APP_DIR="$HOME/.9router"
REPO_DIR="$APP_DIR/upstream"
NINER_DATA="$APP_DIR/data"
LOG="$APP_DIR/bootstrap.log"
SERVER_LOG="$APP_DIR/server.log"
PORT="${PORT:-20128}"
HOST="${HOST:-127.0.0.1}"

mkdir -p "$APP_DIR" "$NINER_DATA"
exec > >(tee -a "$LOG") 2>&1

echo "===================================================="
echo " 9Router Android / Termux Installer"
echo " Time: $(date -Iseconds)"
echo " Port: $PORT"
echo "===================================================="

# 1. Wake lock
echo "==> [1/6] Preventing CPU sleep (termux-wake-lock)"
termux-wake-lock 2>/dev/null || echo "    note: install Termux:API for background lock"

# 2. Base packages
echo "==> [2/6] Installing dependencies (node, python, make, rust, termux-services)"
pkg update -y >/dev/null 2>&1 || true
pkg install -y git nodejs-lts python rust make curl openssl-tool termux-services termux-api >/dev/null 2>&1

echo "    node: $(node -v)  npm: $(npm -v)  git: $(git --version)"

# 3. Clone repo
echo "==> [3/6] Fetching 9router repository"
if [ ! -d "$REPO_DIR/.git" ]; then
  rm -rf "$REPO_DIR"
  git clone --depth 1 --quiet https://github.com/decolua/9router.git "$REPO_DIR"
else
  echo "    existing repository found at $REPO_DIR"
fi

cd "$REPO_DIR"

# 4. Build Next.js standalone
if [ ! -f "$REPO_DIR/.next/standalone/server.js" ]; then
  echo "==> [4/6] Building Next.js standalone (takes 3-6 minutes on phone)..."
  npm install --no-audit --no-fund --loglevel=error
  npm run build
  cp -r public .next/standalone/ 2>/dev/null || true
  cp -r .next/static .next/standalone/.next/ 2>/dev/null || true
else
  echo "==> [4/6] Standalone build already exists, skipping compile"
fi

# 5. Environment configuration
echo "==> [5/6] Configuring secrets and .env"
if [ ! -f "$APP_DIR/.env" ]; then
  JWT_SECRET=$(openssl rand -hex 48)
  API_KEY_SECRET=$(openssl rand -hex 32)
  MACHINE_ID_SALT=$(openssl rand -hex 16)
  INITIAL_PASS=$(openssl rand -hex 8)

  cat > "$APP_DIR/.env" <<EOF
JWT_SECRET=$JWT_SECRET
INITIAL_PASSWORD=$INITIAL_PASS
DATA_DIR=$NINER_DATA
PORT=$PORT
HOSTNAME=0.0.0.0
NODE_ENV=production
API_KEY_SECRET=$API_KEY_SECRET
MACHINE_ID_SALT=$MACHINE_ID_SALT
ENABLE_REQUEST_LOGS=false
OBSERVABILITY_ENABLED=true
AUTH_COOKIE_SECURE=false
REQUIRE_API_KEY=false
BASE_URL=http://$HOST:$PORT
NEXT_PUBLIC_BASE_URL=http://$HOST:$PORT
CLOUD_URL=https://9router.com
NEXT_PUBLIC_CLOUD_URL=https://9router.com
EOF
  chmod 600 "$APP_DIR/.env"
else
  echo "    reusing existing $APP_DIR/.env"
  sed -i "s/^PORT=.*/PORT=$PORT/" "$APP_DIR/.env" || true
fi

# 6. Service registration and startup
echo "==> [6/6] Setting up termux-services and starting 9Router"
SVC_DIR="$PREFIX_DIR/var/service/9router"
mkdir -p "$SVC_DIR/log" "$APP_DIR/svlog"

cat > "$SVC_DIR/run" <<EOF
#!$PREFIX_DIR/bin/sh
cd "$REPO_DIR"
set -a
. "$APP_DIR/.env"
set +a
exec node .next/standalone/server.js 2>&1
EOF
chmod +x "$SVC_DIR/run"

cat > "$SVC_DIR/log/run" <<EOF
#!$PREFIX_DIR/bin/sh
exec svlogd -tt "$APP_DIR/svlog"
EOF
chmod +x "$SVC_DIR/log/run"

# Termux:Boot hook
mkdir -p "$HOME/.termux/boot"
cat > "$HOME/.termux/boot/start-9router" <<EOF
#!$PREFIX_DIR/bin/sh
termux-wake-lock 2>/dev/null || true
sv-enable 9router 2>/dev/null || true
sv up 9router 2>/dev/null || true
EOF
chmod +x "$HOME/.termux/boot/start-9router"

# Stop any previous process
sv down 9router 2>/dev/null || true
pkill -f 'standalone/server.js' 2>/dev/null || true
pkill -f 'custom-server.js' 2>/dev/null || true
sleep 1

# Start via sv or nohup
sv-enable 9router 2>/dev/null || true
sv up 9router 2>/dev/null || true
sleep 3

if ! curl -fsS -o /dev/null "http://127.0.0.1:$PORT/" 2>/dev/null; then
  echo "    sv not responding yet, launching background node process..."
  cd "$REPO_DIR"
  set -a
  . "$APP_DIR/.env"
  set +a
  nohup node .next/standalone/server.js > "$SERVER_LOG" 2>&1 &
fi

# Verification
echo "    waiting for dashboard probe..."
UP=false
for i in $(seq 1 30); do
  if curl -fsS -o /dev/null "http://127.0.0.1:$PORT/" 2>/dev/null || curl -fsS -o /dev/null "http://127.0.0.1:$PORT/dashboard" 2>/dev/null; then
    UP=true
    break
  fi
  sleep 1
done

PASS=$(grep INITIAL_PASSWORD "$APP_DIR/.env" | cut -d'=' -f2)

if [ "$UP" = true ]; then
  echo ""
  echo "===================================================="
  echo "  9Router is RUNNING successfully!"
  echo "  Dashboard : http://127.0.0.1:$PORT/dashboard"
  echo "  Password  : $PASS"
  echo "===================================================="
else
  echo ""
  echo "===================================================="
  echo "  9Router started. Check log: tail -f $SERVER_LOG"
  echo "  Dashboard : http://127.0.0.1:$PORT/dashboard"
  echo "  Password  : $PASS"
  echo "===================================================="
fi
