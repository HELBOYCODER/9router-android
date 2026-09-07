#!/usr/bin/env bash
#
# package_runtime.sh — Assemblies the embedded 9Router runtime for the Android app.
#
# Run this from the repo root (9router-android/) on a build host that has:
#   - Node.js 18+  (used to build the upstream 9Router standalone bundle)
#   - npm
#   - Either a prebuilt Node.js binary for Android aarch64, or ANDROID_NODE_URL set.
#
# Output: app/src/main/assets/9router-runtime/  (committed? No — see .gitignore)
#
# Why: 9Router is a Next.js 16 app (Express custom-server + sql.js/WASM DB fallback).
# We build its `output: "standalone"` bundle on a host (the JS is architecture
# independent), then drop in a Node.js binary compiled for Android ARM64. At runtime
# the app extracts both to its private dir and spawns `node custom-server.js`.
#
# Usage:
#   ./scripts/package_runtime.sh                     # minimal (needs Android node below)
#   ./scripts/package_runtime.sh --node ./node-android-aarch64
#   ANDROID_NODE_URL="https://.../node" ./scripts/package_runtime.sh
#
# Pin the exact upstream commit used for a reproducible bundle.

set -euo pipefail

UPSTREAM_REPO="${UPSTREAM_REPO:-https://github.com/decolua/9router.git}"
UPSTREAM_COMMIT="${UPSTREAM_COMMIT:-eb712ca821f0ba6bc41043fbd14494c5af5daba5}"   # v0.5.69 (2026-09-05)
NODE_MAJOR="${NODE_MAJOR:-22}"          # Node major version the Android binary should be
ANDROID_NODE_URL="${ANDROID_NODE_URL:-}"
PROVIDED_NODE=""
BUILD_TMP="$(mktemp -d)"
OUT="app/src/main/assets/9router-runtime"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

usage() { grep '^#' "$0" | sed -n '2,40p' | sed 's/^# //'; exit 2; }

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --node) PROVIDED_NODE="$2"; shift 2 ;;
    --commit) UPSTREAM_COMMIT="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "Unknown option: $1"; usage ;;
  esac
done

info()  { printf '\033[1;34m[package]\033[0m %s\n' "$*"; }
err()   { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; }

# ---------------------------------------------------------------------------
# 1. Build the 9Router standalone bundle
# ---------------------------------------------------------------------------
info "Building upstream 9Router standalone bundle (commit $UPSTREAM_COMMIT)…"
git clone --depth 1 "$UPSTREAM_REPO" "$BUILD_TMP/9router" >/dev/null 2>&1 || {
  err "git clone failed"; exit 1; }
cd "$BUILD_TMP/9router"
git -c advice.detachedHead=false checkout --quiet "$UPSTREAM_COMMIT" 2>/dev/null || true
npm install --no-audit --no-fund --ignore-scripts >/dev/null 2>&1 || {
  err "npm install failed (need Node 18+ and network)"; exit 1; }
npm run build >/dev/null 2>&1 || {
  err "next build failed"; exit 1; }

STANDALONE="$BUILD_TMP/9router/.next/standalone"
[[ -f "$STANDALONE/server.js" ]] || { err "standalone build not found at $STANDALONE"; exit 1; }

info "Copying standalone build to $OUT/bundle …"
rm -rf "$OUT"
mkdir -p "$OUT/bundle"
# The build + postbuild already placed custom-server.js, .next/static, public inside.
cp -a "$STANDALONE/." "$OUT/bundle/"
# Ensure the wrapper is present even if postbuild skipped it.
if [[ ! -f "$OUT/bundle/custom-server.js" && -f "$BUILD_TMP/9router/custom-server.js" ]]; then
  cp "$BUILD_TMP/9router/custom-server.js" "$OUT/bundle/"
fi

# ---------------------------------------------------------------------------
# 2. Acquire a Node.js binary for Android aarch64
# ---------------------------------------------------------------------------
mkdir -p "$OUT/bin" "$OUT/lib"
NODE_DEST="$OUT/bin/node"

acquire_node_fallback() {
  info "No Node binary supplied. Trying ANDROID_NODE_URL…"
  [[ -z "$ANDROID_NODE_URL" ]] && return 1
  curl -fsSL -o "$BUILD_TMP/node" "$ANDROID_NODE_URL"
  cp "$BUILD_TMP/node" "$NODE_DEST"
  return 0
}

if [[ -n "$PROVIDED_NODE" ]]; then
  cp "$PROVIDED_NODE" "$NODE_DEST"
elif [[ -n "$ANDROID_NODE_URL" && "${ANDROID_NODE_URL:0:4}" == "http" ]]; then
  curl -fsSL -o "$BUILD_TMP/node" "$ANDROID_NODE_URL" || { err "download failed"; exit 1; }
  cp "$BUILD_TMP/node" "$NODE_DEST"
elif ! acquire_node_fallback; then
  err "No Node.js binary for Android provided."
  err "Provide one via --node <path> or set ANDROID_NODE_URL, e.g. a Node ${NODE_MAJOR} build for aarch64-linux-android (nodejs-mobile, or a Termux/static build)."
  err "The binary must be a dynamically-linked aarch64 ELF and any companion .so files placed in $OUT/lib."
  exit 1
fi

chmod +x "$NODE_DEST"

# Copy any companion shared libraries found next to the provided binary.
[[ -n "$PROVIDED_NODE" ]] && {
  PROVIDED_DIR="$(dirname "$PROVIDED_NODE")"
  shopt -s nullglob
  for so in "$PROVIDED_DIR"/*.so "$PROVIDED_DIR"/lib/*.so; do
    [[ -f "$so" ]] && cp "$so" "$OUT/lib/"
  done
  shopt -u nullglob
}

# Verify the binary is aarch64.
arch="$(file -b "$NODE_DEST" 2>/dev/null || true)"
if [[ "$arch" != *aarch64* && "$arch" != *AArch64* && "$arch" != *arm64* && "$arch" != *ARM64* ]]; then
  err "The provided Node binary does not appear to be aarch64/arm64. Detected: ${arch:-unknown}"
  exit 1
fi
info "Node binary: $arch"

# ---------------------------------------------------------------------------
# 3. Manifest
# ---------------------------------------------------------------------------
NODE_VER="$(env -i "$NODE_DEST" -v 2>/dev/null || echo 'android binary (version unknown)')"
cat > "$OUT/runtime-manifest.json" <<EOF
{
  "upstream_repo": "$UPSTREAM_REPO",
  "upstream_commit": "$UPSTREAM_COMMIT",
  "node_version": "${NODE_VER//\"/}",
  "node_abi": "arm64-v8a",
  "packaged_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

rm -rf "$BUILD_TMP"
info "Done. Runtime assembled in $OUT."
echo
info "Next: ./gradlew assembleDebug (Android Studio or CLI with the Android SDK)."
echo
info "Note: to actually exercise the embedded server you can run, on a Linux x64 host:"
info "  (cd '$PWD/$OUT/bundle' && PORT=20128 HOSTNAME=127.0.0.1 node custom-server.js)"
