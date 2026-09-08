#!/bin/sh
set -e

# ==============================================================================
# 9Router Minis Installer & Reinstaller (Android PRoot Alpine aarch64)
# راهنما و اسکریپت نصب و نصب مجدد 9Router روی مینیس
# ==============================================================================

BOLD='\033[1m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

DATA_DIR="/root/.9router"
BACKUP_DIR="/root/.9router/backup"

echo -e "${BOLD}====================================================${NC}"
echo -e "${BOLD} 🚀 9Router Minis Installer (Alpine / PRoot aarch64)${NC}"
echo -e "${BOLD}====================================================${NC}"

# 0. HARD WIPE — destroy previous DB, secrets & backups so old keys die
if [ -d "$DATA_DIR/db" ] || [ -f "$DATA_DIR/jwt-secret" ] || [ -d "$BACKUP_DIR" ]; then
  echo -e "\n${RED}[0/5] 🧨 Hard wipe — invalidating previous keys & database...${NC}"
  rm -rf "$DATA_DIR/db" "$DATA_DIR/jwt-secret" "$DATA_DIR/data.sqlite" 2>/dev/null || true
  rm -rf "$BACKUP_DIR" 2>/dev/null || true
  rm -f "$DATA_DIR/.env" 2>/dev/null || true
  mkdir -p "$DATA_DIR/db"
  echo -e "${YELLOW}  → Previous keys burned, fresh DB will be generated on start${NC}"
fi

# 1. Repair npm symlink if broken
echo -e "\n${YELLOW}[1/5] Checking Node & npm toolchain...${NC}"
if [ -L "/usr/local/bin/npm" ]; then
  TARGET=$(readlink /usr/local/bin/npm)
  if echo "$TARGET" | grep -q "node"; then
    echo "Fixing broken /usr/local/bin/npm symlink -> /usr/bin/npm"
    ln -sf /usr/bin/npm /usr/local/bin/npm
  fi
elif [ ! -x "/usr/local/bin/npm" ] && [ -x "/usr/bin/npm" ]; then
  ln -sf /usr/bin/npm /usr/local/bin/npm
fi

if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  echo "Installing nodejs and npm via apk..."
  apk add --no-cache nodejs npm
fi

echo -e "${GREEN}✓ Node: $(node -v) | npm: $(npm -v)${NC}"

# 2. Install 9router latest
echo -e "\n${YELLOW}[2/5] Installing 9router package globally...${NC}"
npm install -g 9router@latest --prefer-online

ROUTER_VER=$(9router -v 2>/dev/null || echo "installed")
echo -e "${GREEN}✓ 9Router version: $ROUTER_VER${NC}"

# 3. Deploy daemon service manager & reinstall shortcut
echo -e "\n${YELLOW}[3/5] Installing 9router-service & 9router-reinstall CLI...${NC}"
DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$DIR/bin/9router-service" ]; then
  cp "$DIR/bin/9router-service" /usr/local/bin/9router-service
  cp "$DIR/bin/9router-reinstall" /usr/local/bin/9router-reinstall
else
  curl -sSL "https://raw.githubusercontent.com/HELBOYCODER/9router-minis/main/bin/9router-service" -o /usr/local/bin/9router-service
  curl -sSL "https://raw.githubusercontent.com/HELBOYCODER/9router-minis/main/bin/9router-reinstall" -o /usr/local/bin/9router-reinstall
fi
chmod +x /usr/local/bin/9router-service /usr/local/bin/9router-reinstall
echo -e "${GREEN}✓ Installed: /usr/local/bin/9router-service${NC}"
echo -e "${GREEN}✓ Installed: /usr/local/bin/9router-reinstall${NC}"

# 4. Fresh DB — will be auto-created on first start; inject OpenCode Free afterwards
mkdir -p "$DATA_DIR/db"
echo -e "${CYAN}Fresh database — new secrets will be generated on daemon start${NC}"
# OpenCode Free injection is deferred until after health check (DB must exist first)

# 5. Start daemon
echo -e "\n${YELLOW}[4/5] Starting 9Router background daemon...${NC}"
/usr/local/bin/9router-service restart

# 6. Verify Health and inject OpenCode Free into fresh DB
echo -e "\n${YELLOW}[5/5] Checking health status...${NC}"
sleep 2
# Inject OpenCode Free provider after DB is created by the daemon
if [ -f "$DATA_DIR/db/data.sqlite" ]; then
  sqlite3 "$DATA_DIR/db/data.sqlite" "INSERT OR IGNORE INTO providerConnections (id, provider, authType, name, priority, isActive, data, createdAt, updatedAt) VALUES ('opencode-free-conn', 'opencode', 'noAuth', 'OpenCode Free', 1, 1, '{\"apiKey\":\"public\"}', datetime('now'), datetime('now'));" 2>/dev/null || true
  echo -e "${GREEN}✓ OpenCode Free provider ensured${NC}"
fi
if /usr/local/bin/9router-service status; then
  LAN_IP=$(ip route get 1.1.1.1 2>/dev/null | awk '{print $7}' | grep -E '^[0-9.]+$' | head -n 1)
  echo -e "\n${GREEN}${BOLD}====================================================${NC}"
  echo -e "${GREEN}${BOLD} 🎉 9Router successfully running on Minis!${NC}"
  echo -e "${GREEN}${BOLD}====================================================${NC}"
  echo -e "Local Address:   http://127.0.0.1:20128"
  [ -n "$LAN_IP" ] && echo -e "LAN Address:     http://$LAN_IP:20128"
  echo -e "Dashboard:       http://127.0.0.1:20128/dashboard"
  echo -e "Password:        123456"
  echo -e "API Base URL:    http://127.0.0.1:20128/v1"
  echo -e "Default API Key: sk-test"
  echo -e ""
  echo -e "Commands:"
  echo -e "  9router-reinstall         # Hard reinstall — old keys invalidated, fresh build"
  echo -e "  9router-service status    # Check status"
  echo -e "  9router-service logs      # Live logs"
  echo -e "  9router-service restart   # Restart daemon"
  echo -e "  9router-service stop      # Stop daemon"
else
  echo -e "\n${RED}Service started but health check timed out. Check logs:${NC}"
  /usr/local/bin/9router-service logs 30
  exit 1
fi
