#!/bin/sh
PORT="${1:-20128}"
URL="http://127.0.0.1:$PORT"

echo "Checking 9Router on $URL..."

HEALTH=$(curl -s "$URL/api/health" 2>/dev/null || echo "")
if echo "$HEALTH" | grep -q '{"ok":true}'; then
  echo "✅ Health check OK: $HEALTH"
else
  echo "❌ Health check failed: $HEALTH"
  exit 1
fi

MODELS=$(curl -s "$URL/v1/models" 2>/dev/null | grep -o '"id":"[^"]*"' | head -n 5)
if [ -n "$MODELS" ]; then
  echo "✅ Models discovered:"
  echo "$MODELS"
else
  echo "⚠️ Could not list models (check if requireApiKey is enabled)"
fi
