#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" != "--confirm" ]]; then
  echo "Usage: $0 --confirm" >&2
  exit 2
fi
CONFIG_PATH="${HOME}/.smartstock/database.properties"
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
"$APP_DIR/tools/lan-api-cutover-check.sh"
security find-generic-password -w -s com.smartstock.database -a lan-api-device-token >/dev/null \
  || { echo "This register has not claimed its approved LAN API credential." >&2; exit 1; }
chmod 600 "$CONFIG_PATH"
for key in device-db-user device-db-password primary-db-user primary-db-password; do
  security delete-generic-password -s com.smartstock.database -a "$key" >/dev/null 2>&1 || true
done
echo "This register now uses only the authenticated SmartStock LAN service."
