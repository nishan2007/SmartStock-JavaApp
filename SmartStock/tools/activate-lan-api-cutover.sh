#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" != "--confirm" ]]; then
  echo "Usage: $0 --confirm" >&2
  echo "Runs only on the physical SmartStock server after all registers are upgraded and paired." >&2
  exit 2
fi

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CONFIG_PATH="${HOME}/.smartstock/database.properties"
"$APP_DIR/tools/lan-api-cutover-check.sh"

if [[ ! -f "$CONFIG_PATH" ]]; then
  echo "SmartStock server configuration is missing: $CONFIG_PATH" >&2
  exit 1
fi

DB_USER="$(security find-generic-password -w -s com.smartstock.database -a primary-db-user 2>/dev/null || true)"
DB_PASSWORD="$(security find-generic-password -w -s com.smartstock.database -a primary-db-password 2>/dev/null || true)"
if [[ -f "${HOME}/.smartstock/database-credentials.txt" ]]; then
  # Upgrade an older server by importing its existing server credential into
  # Keychain before the plaintext note is scrubbed below.
  # shellcheck disable=SC1090
  source "${HOME}/.smartstock/database-credentials.txt"
  DB_USER="${DB_USER:-${SMARTSTOCK_DB_USER:-}}"
  DB_PASSWORD="${DB_PASSWORD:-${SMARTSTOCK_DB_PASSWORD:-}}"
fi
DB_USER="${DB_USER#\'}"
DB_USER="${DB_USER%\'}"
DB_PASSWORD="${DB_PASSWORD#\'}"
DB_PASSWORD="${DB_PASSWORD%\'}"
if [[ -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
  echo "SmartStock server database credentials are missing. Run the server installer once before activating the cutover." >&2
  exit 1
fi
security add-generic-password -U -s com.smartstock.database -a primary-db-user -w "$DB_USER" >/dev/null
security add-generic-password -U -s com.smartstock.database -a primary-db-password -w "$DB_PASSWORD" >/dev/null
DB_PORT="$(awk -F= '$1 == "server.port" {print $2}' "$CONFIG_PATH" | tail -n 1)"
DB_PORT="${DB_PORT:-5432}"
if [[ "$(psql -d postgres -Atc "SELECT rolsuper FROM pg_roles WHERE rolname=current_user" 2>/dev/null || true)" == "t" ]]; then
  ADMIN_PSQL=(psql -d smartstock)
else
  ADMIN_PSQL=(psql -h 127.0.0.1 -p "$DB_PORT" -U "$DB_USER" -d smartstock)
  export PGPASSWORD="$DB_PASSWORD"
fi
HBA_FILE="$("${ADMIN_PSQL[@]}" -Atc 'show hba_file')"
cp "$HBA_FILE" "$HBA_FILE.smartstock-api-cutover-$(date +%Y%m%d%H%M%S)"
# SmartStock is pre-launch and has no mixed-client compatibility period. Device
# enrollment is validated separately; database isolation is never delayed by an
# unpaired development device.
if [[ -n "${SMARTSTOCK_REQUIRED_APP_VERSION:-}" ]]; then
  WRONG_VERSION_COUNT="$(PGPASSWORD="$DB_PASSWORD" psql -h 127.0.0.1 -p "$DB_PORT" -U "$DB_USER" -d smartstock -Atc "SELECT count(*) FROM devices WHERE is_approved AND NOT is_blocked AND last_seen > CURRENT_TIMESTAMP - INTERVAL '30 days' AND COALESCE(app_version, '') <> :'required_version'" --set=required_version="$SMARTSTOCK_REQUIRED_APP_VERSION")"
  if [[ "$WRONG_VERSION_COUNT" != "0" ]]; then
    echo "Cutover blocked: $WRONG_VERSION_COUNT active register(s) are not on required version $SMARTSTOCK_REQUIRED_APP_VERSION." >&2
    exit 1
  fi
fi

"${ADMIN_PSQL[@]}" -v ON_ERROR_STOP=1 <<'SQL'
DO $$
DECLARE role_name text;
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartstock_client') THEN
    ALTER ROLE smartstock_client NOLOGIN;
  END IF;
  FOR role_name IN
    SELECT rolname FROM pg_roles WHERE rolname LIKE 'smartstock_device_%'
  LOOP
    EXECUTE format('ALTER ROLE %I NOLOGIN', role_name);
  END LOOP;
END $$;
INSERT INTO security_audit_events(event_type, details)
VALUES ('LAN_API_CUTOVER_ACTIVATED', 'Direct register JDBC roles disabled after zero-JDBC architecture check');
ALTER SYSTEM SET listen_addresses = 'localhost';
SQL

# Remove the installer-managed LAN database rule. The HTTPS service is now the
# only register-facing port.
perl -0pi -e "s/^([[:space:]]*host(?:ssl|nossl)?[[:space:]]+smartstock[[:space:]]+[^[:space:]]+[[:space:]]+samenet[[:space:]]+.*)$/# disabled by SmartStock LAN API cutover: $1/mg" "$HBA_FILE"
perl -0pi -e "s/^([[:space:]]*host(?:ssl|nossl)?[[:space:]]+[^[:space:]]+[[:space:]]+smartstock_(?:client|device_)[^[:space:]]*[[:space:]]+.*)$/# disabled by SmartStock LAN API cutover: $1/mg" "$HBA_FILE"

chmod 600 "$CONFIG_PATH"
security delete-generic-password -s com.smartstock.database -a device-db-user >/dev/null 2>&1 || true
security delete-generic-password -s com.smartstock.database -a device-db-password >/dev/null 2>&1 || true
CREDENTIALS_NOTE="${HOME}/.smartstock/database-credentials.txt"
if [[ -f "$CREDENTIALS_NOTE" ]]; then
  cp "$CREDENTIALS_NOTE" "$CREDENTIALS_NOTE.pre-lan-api-lockdown"
fi
for credentials_file in "${HOME}/.smartstock"/database-credentials.txt*; do
  [[ -f "$credentials_file" ]] || continue
  perl -0pi -e 's/^SMARTSTOCK_(?:CLIENT_DB_[A-Z_]+|DB_PASSWORD)=.*\n//mg; s/^# Use these on register\/client computers\.\n//mg' "$credentials_file"
  chmod 600 "$credentials_file"
done
for properties_file in "${HOME}/.smartstock"/database.properties.bak.*; do
  [[ -f "$properties_file" ]] || continue
  perl -0pi -e 's/^db\.user=.*/db.user=\${SMARTSTOCK_SECURE_DB_USER}/mg; s/^db\.password=.*/db.password=\${SMARTSTOCK_SECURE_DB_PASSWORD}/mg; s/^cloud\.db\.user=.*/cloud.db.user=\${SMARTSTOCK_SECURE_CLOUD_DB_USER}/mg; s/^cloud\.db\.password=.*/cloud.db.password=\${SMARTSTOCK_SECURE_CLOUD_DB_PASSWORD}/mg' "$properties_file"
  chmod 600 "$properties_file"
done
formula="$(brew services list 2>/dev/null | awk '/^postgresql(@[0-9]+)?[[:space:]]/ {print $1}' | sort -Vr | head -n 1)"
if [[ -n "$formula" ]]; then brew services restart "$formula"; fi
echo "SmartStock LAN API lockdown verified. Direct register database roles are disabled."
