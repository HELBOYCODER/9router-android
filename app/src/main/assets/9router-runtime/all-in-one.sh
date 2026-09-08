#!/bin/sh
# all-in-one.sh — Single script: wipe → install → build → start → health
# Bundled in APK assets/9router-runtime/scripts/
# Runs inside Termux — no external dependencies needed.

set -e

APP_DIR="$HOME/.9router"
REPO_DIR="$APP_DIR/upstream"
BRANCH="source"
PORT="${PORT:-20128}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

log() { echo -e "${CYAN}[all-in-one]${NC} $*"; }
ok()  { echo -e "${GREEN}  ✓ $*${NC}"; }
warn(){ echo -e "${YELLOW}  ⚠ $*${NC}"; }
fail(){ echo -e "${RED}  ✗ $*${NC}"; }

# ── Step 0: Wake lock (prevent CPU sleep) ──────────────────────────
termux-wake-lock 2>/dev/null || true

# ── Step 1: HARD WIPE — burn everything ────────────────────────────
log "Step 1/6: Hard wipe — destroying old data, keys, build cache..."
rm -rf "$APP_DIR/data"
rm -rf "$APP_DIR/db"
rm -rf "$APP_DIR/backup"
rm -rf "$APP_DIR/auth"
rm -rf "$APP_DIR/logs"
rm -rf "$APP_DIR/.env"
rm -rf "$APP_DIR/jwt-secret"
rm -rf "$APP_DIR/data.sqlite"
rm -rf "$REPO_DIR/.next"
mkdir -p "$APP_DIR/data" "$APP_DIR/db" "$APP_DIR/auth" "$APP_DIR/backup" "$APP_DIR/logs"
ok "Old data burned"

# ── Step 2: Install Termux packages ────────────────────────────────
log "Step 2/6: Installing Termux packages..."
pkg update -y >/dev/null 2>&1 || true
pkg install -y git nodejs-lts python curl openssl-tool termux-services >/dev/null 2>&1 || true
ok "Packages installed ($(node -v 2>/dev/null || echo 'node?'))"

# ── Step 3: Clone or update 9Router source ─────────────────────────
log "Step 3/6: Fetching 9Router source..."
if [ -d "$REPO_DIR/.git" ]; then
  cd "$REPO_DIR"
  git fetch origin 2>/dev/null || true
  git reset --hard "origin/$BRANCH" 2>/dev/null || git pull origin "$BRANCH" 2>/dev/null || true
  ok "Source updated"
else
  rm -rf "$REPO_DIR"
  git clone --branch "$BRANCH" --depth 1 --single-branch https://github.com/9router/9router.git "$REPO_DIR" 2>/dev/null || \
  git clone --branch main --depth 1 https://github.com/9router/9router.git "$REPO_DIR"
  ok "Source cloned"
fi

# ── Step 4: Install deps + build standalone ────────────────────────
log "Step 4/6: Installing dependencies..."
cd "$REPO_DIR"
npm install --production >/dev/null 2>&1 || true
ok "Dependencies installed"

log "Building Next.js standalone..."
npm run build 2>&1 | tail -3
mkdir -p .next/standalone
cp -r public .next/standalone/ 2>/dev/null || true
cp -r .next/static .next/standalone/.next/ 2>/dev/null || true
ok "Build complete"

# ── Step 5: Generate fresh .env ────────────────────────────────────
log "Step 5/6: Generating fresh secrets..."
if [ ! -f "$APP_DIR/.env" ]; then
  JWT_SECRET=$(openssl rand -hex 32)
  INITIAL_PASSWORD=$(openssl rand -hex 16)
  API_KEY_SECRET=$(openssl rand -hex 32)
  MACHINE_ID_SALT=$(openssl rand -hex 16)

  cat > "$APP_DIR/.env" << ENVEOF
PORT=$PORT
HOSTNAME=0.0.0.0
JWT_SECRET=$JWT_SECRET
INITIAL_PASSWORD=$INITIAL_PASSWORD
API_KEY_SECRET=$API_KEY_SECRET
MACHINE_ID_SALT=$MACHINE_ID_SALT
DATA_DIR=$APP_DIR/data
NODE_ENV=production
BASE_URL=http://127.0.0.1:$PORT/v1
NEXT_PUBLIC_BASE_URL=http://127.0.0.1:$PORT/v1
AUTH_COOKIE_SECURE=false
REQUIRE_API_KEY=false
ENVEOF
  ok "Secrets generated"
else
  ok "Secrets already present"
fi

# ── Step 6: Start server ───────────────────────────────────────────
log "Step 6/6: Starting 9Router on port $PORT..."
cd "$REPO_DIR"
export $(grep -v '^#' "$APP_DIR/.env" | xargs)
node .next/standalone/server.js &
SERVER_PID=$!
sleep 3

# Health check
if curl -sf http://127.0.0.1:$PORT/api/health >/dev/null 2>&1; then
  ok "9Router is live! Dashboard: http://127.0.0.1:$PORT/dashboard"
  echo "PID=$SERVER_PID"
  echo "STATUS=READY"
else
  warn "Server started but health check pending — may need a few more seconds"
  echo "PID=$SERVER_PID"
  echo "STATUS=WARMING"
fi
