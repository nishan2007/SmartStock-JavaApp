#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
MODE="server"
RESET_LOCAL_DB="no"
for arg in "$@"; do
  case "$arg" in
    server|client)
      MODE="$arg"
      ;;
    --reset-local-db)
      RESET_LOCAL_DB="yes"
      ;;
    --help|-h)
      cat <<'EOF'
SmartStock macOS installer

Usage:
  install.command [server|client] [--reset-local-db]

Default behavior is an in-place repair/upgrade:
  - installs missing runtime dependencies
  - starts PostgreSQL
  - creates/repairs SmartStock DB roles
  - creates the database if missing
  - reapplies schema/grants
  - rewrites SmartStock config and credentials files
  - rebuilds launchers

--reset-local-db is destructive. It drops and recreates the local SmartStock
database after confirmation.
EOF
      exit 0
      ;;
  esac
done
DB_NAME="${SMARTSTOCK_DB_NAME:-smartstock}"
DB_USER="${SMARTSTOCK_DB_USER:-smartstock_server}"
DB_PASSWORD="${SMARTSTOCK_DB_PASSWORD:-}"
DB_PORT="${SMARTSTOCK_DB_PORT:-5432}"
SYNC_INTERVAL="${SMARTSTOCK_SYNC_INTERVAL:-60}"
CREDENTIALS_PATH="${HOME}/.smartstock/database-credentials.txt"
CONFIG_PATH="${HOME}/.smartstock/database.properties"
BACKUP_SUFFIX="$(date +%Y%m%d-%H%M%S)"

log() {
  printf '\n==> %s\n' "$1"
}

require_command() {
  command -v "$1" >/dev/null 2>&1
}

generate_password() {
  if require_command openssl; then
    openssl rand -base64 24 | tr -d '/+=' | cut -c 1-24
  else
    uuidgen | tr -d '-' | cut -c 1-24
  fi
}

looks_like_placeholder() {
  [[ "${1:-}" =~ ^SMARTSTOCK_[A-Z0-9_]+$ || "${1:-}" =~ ^\$\{SMARTSTOCK_[A-Z0-9_]+\}$ ]]
}

clean_placeholder() {
  local value="${1:-}"
  if looks_like_placeholder "$value"; then
    printf ''
  else
    printf '%s' "$value"
  fi
}

load_or_create_credentials() {
  mkdir -p "${HOME}/.smartstock"
  if [[ -f "$CREDENTIALS_PATH" ]]; then
    # shellcheck disable=SC1090
    source "$CREDENTIALS_PATH"
    DB_USER="${SMARTSTOCK_DB_USER:-$DB_USER}"
    DB_PASSWORD="${SMARTSTOCK_DB_PASSWORD:-$DB_PASSWORD}"
  fi
  if [[ -z "$DB_USER" ]] || looks_like_placeholder "$DB_USER"; then
    DB_USER="$(security find-generic-password -w -s com.smartstock.database -a primary-db-user 2>/dev/null || true)"
  fi
  if [[ -z "$DB_PASSWORD" ]] || looks_like_placeholder "$DB_PASSWORD"; then
    DB_PASSWORD="$(security find-generic-password -w -s com.smartstock.database -a primary-db-password 2>/dev/null || true)"
  fi
  DB_USER="$(clean_placeholder "$DB_USER")"
  DB_PASSWORD="$(clean_placeholder "$DB_PASSWORD")"
  if [[ -z "$DB_USER" ]]; then
    DB_USER="smartstock_server"
  fi
  if [[ -z "$DB_PASSWORD" ]]; then
    DB_PASSWORD="$(generate_password)"
  fi
}

backup_existing_files() {
  mkdir -p "${HOME}/.smartstock"
  if [[ -f "$CONFIG_PATH" ]]; then
    cp "$CONFIG_PATH" "${CONFIG_PATH}.bak.${BACKUP_SUFFIX}"
  fi
  if [[ -f "$CREDENTIALS_PATH" ]]; then
    cp "$CREDENTIALS_PATH" "${CREDENTIALS_PATH}.bak.${BACKUP_SUFFIX}"
  fi
}

install_homebrew_if_needed() {
  if require_command brew; then
    return
  fi
  log "Installing Homebrew"
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
  if [[ -x /opt/homebrew/bin/brew ]]; then
    eval "$(/opt/homebrew/bin/brew shellenv)"
  elif [[ -x /usr/local/bin/brew ]]; then
    eval "$(/usr/local/bin/brew shellenv)"
  fi
}

install_dependencies() {
  install_homebrew_if_needed
  log "Installing Java, Maven, and PostgreSQL when missing"
  require_command java || brew install openjdk@17
  require_command mvn || brew install maven
  require_command psql || brew install postgresql

  if [[ -x /opt/homebrew/opt/openjdk@17/bin/java ]]; then
    export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
  elif [[ -x /usr/local/opt/openjdk@17/bin/java ]]; then
    export PATH="/usr/local/opt/openjdk@17/bin:$PATH"
  fi
  add_postgres_to_path
}

install_client_dependencies() {
  install_homebrew_if_needed
  log "Installing Java and Maven when missing"
  require_command java || brew install openjdk@17
  require_command mvn || brew install maven
  if [[ -x /opt/homebrew/opt/openjdk@17/bin/java ]]; then
    export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
  elif [[ -x /usr/local/opt/openjdk@17/bin/java ]]; then
    export PATH="/usr/local/opt/openjdk@17/bin:$PATH"
  fi
}

postgres_formula() {
  brew services list 2>/dev/null | awk '/^postgresql(@[0-9]+)?[[:space:]]/ {print $1}' | sort -Vr | head -n 1
}

add_postgres_to_path() {
  local formula
  formula="$(postgres_formula || true)"
  if [[ -z "$formula" ]]; then
    formula="postgresql"
  fi
  if [[ -d "/opt/homebrew/opt/${formula}/bin" ]]; then
    export PATH="/opt/homebrew/opt/${formula}/bin:$PATH"
  elif [[ -d "/usr/local/opt/${formula}/bin" ]]; then
    export PATH="/usr/local/opt/${formula}/bin:$PATH"
  fi
}

start_postgres() {
  log "Starting PostgreSQL service"
  local formula
  formula="$(postgres_formula || true)"
  if [[ -z "$formula" ]]; then
    formula="postgresql"
  fi
  brew services start "$formula" || brew services restart "$formula" || true
  add_postgres_to_path
  psql --version
}

reset_database_if_requested() {
  if [[ "$RESET_LOCAL_DB" != "yes" ]]; then
    return
  fi
  if [[ "${SMARTSTOCK_CONFIRM_RESET:-}" != "YES" ]]; then
    cat <<EOF

Refusing to reset the local database without confirmation.

This would permanently drop database '${DB_NAME}'.
To proceed, rerun with:

SMARTSTOCK_CONFIRM_RESET=YES SmartStock/installer/macos/install.command ${MODE} --reset-local-db

EOF
    exit 1
  fi

  log "Resetting local SmartStock database"
  local postgres_url="postgresql://127.0.0.1:${DB_PORT}/postgres"
  psql "$postgres_url" \
    -v ON_ERROR_STOP=1 \
    -v db_name="$DB_NAME" <<'SQL'
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = :'db_name'
  AND pid <> pg_backend_pid();

SELECT format('DROP DATABASE IF EXISTS %I', :'db_name')\gexec
SQL
}

init_database() {
  log "Creating or repairing SmartStock local database and roles"
  local postgres_url="postgresql://127.0.0.1:${DB_PORT}/postgres"

  psql "$postgres_url" \
    -v ON_ERROR_STOP=1 \
    -v db_name="$DB_NAME" \
    -v db_user="$DB_USER" \
    -v db_password="$DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_user', :'db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_user')\gexec

SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'db_user', :'db_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_user')\gexec

SELECT 'CREATE ROLE service_role NOLOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role')\gexec

SELECT 'CREATE ROLE authenticated NOLOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated')\gexec

SELECT 'CREATE ROLE anon NOLOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'db_name', :'db_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db_name')\gexec

SELECT format('GRANT ALL PRIVILEGES ON DATABASE %I TO %I', :'db_name', :'db_user')\gexec

SQL

  psql "postgresql://${DB_USER}:${DB_PASSWORD}@127.0.0.1:${DB_PORT}/${DB_NAME}" \
    -v ON_ERROR_STOP=1 \
    -f "${APP_DIR}/database/base_schema_setup.sql"

  psql "postgresql://${DB_USER}:${DB_PASSWORD}@127.0.0.1:${DB_PORT}/${DB_NAME}" \
    -v ON_ERROR_STOP=1 \
    -f "${APP_DIR}/database/local_network_sync_setup.sql"

  apply_feature_schema_scripts

}

apply_feature_schema_scripts() {
  log "Applying SmartStock feature schema scripts"
  local scripts=(
    permission_descriptions_setup.sql
    permission_descriptions_and_sections_backfill.sql
    location_management_setup.sql
    store_timezone_setup.sql
    department_setup.sql
    item_details_setup.sql
    vendor_setup.sql
    held_cart_setup.sql
    product_type_setup.sql
    product_size_setup.sql
    product_sku_setup.sql
    customer_type_setup.sql
    device_management_setup.sql
    lan_api_security_setup.sql
    hardware_setup_permission.sql
    company_customization_setup.sql
    company_customization_permission.sql
    company_preferences_permission.sql
    custom_orders_setup.sql
    custom_order_sku_setup.sql
    custom_order_controls_setup.sql
    custom_order_safety_controls_setup.sql
    custom_order_line_discount_setup.sql
    payment_mmg_setup.sql
    returns_setup.sql
    sale_discount_setup.sql
    sale_override_controls_setup.sql
    sales_transaction_source_setup.sql
    normal_sales_audit_setup.sql
    cash_drawer_management_setup.sql
    balance_sheet_expenses_setup.sql
    time_clock_setup.sql
    store_transfer_setup.sql
    end_of_day_setup.sql
    maintenance_management_setup.sql
    inventory_sensitive_permissions.sql
  )
  for script in "${scripts[@]}"; do
    if [[ -f "${APP_DIR}/database/${script}" ]]; then
      psql "postgresql://${DB_USER}:${DB_PASSWORD}@127.0.0.1:${DB_PORT}/${DB_NAME}" \
        -v ON_ERROR_STOP=1 \
        -f "${APP_DIR}/database/${script}"
    fi
  done
}

restrict_postgres_to_server() {
  log "Restricting PostgreSQL to the physical server"
  local postgres_url="postgresql://127.0.0.1:${DB_PORT}/postgres"
  local hba_file
  hba_file="$(psql "$postgres_url" -Atc 'show hba_file')"
  cp "$hba_file" "$hba_file.smartstock-service-only-backup-${BACKUP_SUFFIX}"
  psql "$postgres_url" -v ON_ERROR_STOP=1 <<'SQL'
DO $$
DECLARE role_name text;
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartstock_client') THEN
    ALTER ROLE smartstock_client NOLOGIN;
  END IF;
  FOR role_name IN SELECT rolname FROM pg_roles WHERE rolname LIKE 'smartstock_device_%' LOOP
    EXECUTE format('ALTER ROLE %I NOLOGIN', role_name);
  END LOOP;
END $$;
ALTER SYSTEM SET listen_addresses = 'localhost';
SQL
  perl -0pi -e "s/^([[:space:]]*host(?:ssl|nossl)?[[:space:]]+${DB_NAME}[[:space:]]+[^[:space:]]+[[:space:]]+samenet[[:space:]]+.*)$/# disabled by SmartStock HTTPS single cutover: \$1/mg" "$hba_file"
  perl -0pi -e "s/^([[:space:]]*host(?:ssl|nossl)?[[:space:]]+[^[:space:]]+[[:space:]]+smartstock_(?:client|device_)[^[:space:]]*[[:space:]]+.*)$/# disabled by SmartStock HTTPS single cutover: \$1/mg" "$hba_file"
  local formula
  formula="$(postgres_formula || true)"
  [[ -n "$formula" ]] || formula="postgresql"
  brew services restart "$formula"
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    pg_isready -h 127.0.0.1 -p "$DB_PORT" -q && break
    sleep 1
  done
  [[ "$(psql "$postgres_url" -Atc 'show listen_addresses')" == "localhost" ]]
}

write_config() {
  log "Writing SmartStock database config"
  mkdir -p "${HOME}/.smartstock"
  security add-generic-password -U -s com.smartstock.database -a primary-db-user -w "$DB_USER" >/dev/null
  security add-generic-password -U -s com.smartstock.database -a primary-db-password -w "$DB_PASSWORD" >/dev/null
  cat > "$CONFIG_PATH" <<EOF
# SmartStock database mode and sync configuration
mode=$(printf '%s' "$MODE" | tr '[:lower:]-' '[:upper:]_')
server.host=127.0.0.1
server.port=${DB_PORT}
lan.api.port=8443
database.name=${DB_NAME}
jdbc.url=jdbc:postgresql://127.0.0.1:${DB_PORT}/${DB_NAME}
db.user=\${SMARTSTOCK_SECURE_DB_USER}
db.password=\${SMARTSTOCK_SECURE_DB_PASSWORD}
sync.interval.seconds=${SYNC_INTERVAL}
EOF
  chmod 600 "$CONFIG_PATH"
}

write_client_config() {
  log "Writing register HTTPS service config"
  local lan_host="${SMARTSTOCK_LAN_SERVER_HOST:-127.0.0.1}"
  local lan_port="${SMARTSTOCK_LAN_API_PORT:-8443}"
  mkdir -p "${HOME}/.smartstock"
  cat > "$CONFIG_PATH" <<EOF
# SmartStock register mode. Registers have no database or cloud credentials.
mode=CLIENT
server.host=${lan_host}
server.port=${lan_port}
sync.interval.seconds=${SYNC_INTERVAL}
EOF
  chmod 600 "$CONFIG_PATH"
  security add-generic-password -U -s com.smartstock.database -a lan-api-server-host -w "$lan_host" >/dev/null
  security add-generic-password -U -s com.smartstock.database -a lan-api-server-port -w "$lan_port" >/dev/null
  security delete-generic-password -s com.smartstock.database -a primary-db-user >/dev/null 2>&1 || true
  security delete-generic-password -s com.smartstock.database -a primary-db-password >/dev/null 2>&1 || true
  security delete-generic-password -s com.smartstock.database -a cloud-db-user >/dev/null 2>&1 || true
  security delete-generic-password -s com.smartstock.database -a cloud-db-password >/dev/null 2>&1 || true
  rm -f "$CREDENTIALS_PATH"
}

write_credentials_note() {
  log "Writing database credentials note"
  local server_host
  server_host="$(scutil --get LocalHostName 2>/dev/null || hostname -s || true)"
  if [[ -n "$server_host" && "$server_host" != *.local ]]; then
    server_host="${server_host}.local"
  fi
  if [[ -z "$server_host" ]]; then
    server_host="<SERVER-HOSTNAME-OR-IP>"
  fi
  cat > "$CREDENTIALS_PATH" <<EOF
# SmartStock local database credentials
# Keep this file private. It is used by the installer if you rerun setup.

SMARTSTOCK_DB_NAME='${DB_NAME}'
SMARTSTOCK_DB_PORT='${DB_PORT}'

# The dedicated server password is stored in macOS Keychain, not in this file.
SMARTSTOCK_DB_USER='${DB_USER}'
SMARTSTOCK_SERVER_JDBC_URL='jdbc:postgresql://127.0.0.1:${DB_PORT}/${DB_NAME}'

# Registers use the pinned SmartStock HTTPS service and receive no database credentials.
SMARTSTOCK_LAN_API_URL='https://${server_host}:8443'
EOF
  chmod 600 "$CREDENTIALS_PATH"
}

build_app() {
  log "Running LAN cutover/security checks and building SmartStock"
  cd "$APP_DIR"
  "${APP_DIR}/tools/lan-api-cutover-check.sh"
  "${APP_DIR}/tools/security-check.sh"
  mvn -q clean package
}

write_launchers() {
  log "Writing launchers"
  mkdir -p "${APP_DIR}/target"
  local jar_path
  jar_path="$(find "${APP_DIR}/target" -maxdepth 1 -name 'inventory-management-*.jar' -type f | sort | tail -n 1)"
  if [[ -z "$jar_path" ]]; then
    printf 'No SmartStock jar found in %s/target. Run mvn package first.\n' "$APP_DIR" >&2
    exit 1
  fi
  local jar_name
  jar_name="$(basename "$jar_path")"
  if [[ "$(printf '%s' "$MODE" | tr '[:lower:]-' '[:upper:]_')" == "SERVER" ]]; then
    cat > "${APP_DIR}/target/run-smartstock-server.command" <<EOF
#!/usr/bin/env bash
cd "${APP_DIR}"
java -jar target/${jar_name} --server
EOF
    chmod +x "${APP_DIR}/target/run-smartstock-server.command"
  else
    rm -f "${APP_DIR}/target/run-smartstock-server.command"
  fi

  cat > "${APP_DIR}/target/run-smartstock-client.command" <<EOF
#!/usr/bin/env bash
cd "${APP_DIR}"
java -jar target/${jar_name} --client
EOF
  chmod +x "${APP_DIR}/target/run-smartstock-client.command"

  if [[ "$(printf '%s' "$MODE" | tr '[:lower:]-' '[:upper:]_')" != "SERVER" ]]; then
    return
  fi

  local service_dir="${HOME}/.smartstock/sync-service"
  local service_app_dir="${service_dir}/app"
  mkdir -p "${service_app_dir}/dependency"
  rm -f "${service_app_dir}"/inventory-management-*.jar
  cp "$jar_path" "${service_app_dir}/"
  if [[ -d "${APP_DIR}/target/dependency" ]]; then
    rm -rf "${service_app_dir}/dependency"
    cp -R "${APP_DIR}/target/dependency" "${service_app_dir}/dependency"
  fi
cat > "${service_dir}/run-smartstock-sync-service.command" <<EOF
#!/usr/bin/env bash
cd "${service_app_dir}"
exec java -Djava.awt.headless=true -Dapple.awt.UIElement=true -jar ${jar_name} --sync-service
EOF
  chmod +x "${service_dir}/run-smartstock-sync-service.command"
}

install_sync_launch_agent() {
  if [[ "$(printf '%s' "$MODE" | tr '[:lower:]-' '[:upper:]_')" != "SERVER" ]]; then
    return
  fi
  log "Installing SmartStock Server Service (LAN API and background sync)"
  mkdir -p "${HOME}/Library/LaunchAgents" "${HOME}/.smartstock"
  local plist="${HOME}/Library/LaunchAgents/com.smartstock.sync.plist"
  cat > "$plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>com.smartstock.sync</string>
  <key>ProgramArguments</key>
  <array>
    <string>${HOME}/.smartstock/sync-service/run-smartstock-sync-service.command</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>StandardOutPath</key>
  <string>${HOME}/.smartstock/sync-service.log</string>
  <key>StandardErrorPath</key>
  <string>${HOME}/.smartstock/sync-service.err.log</string>
  <key>WorkingDirectory</key>
  <string>${HOME}/.smartstock/sync-service/app</string>
</dict>
</plist>
EOF
  launchctl bootout "gui/$(id -u)" "$plist" >/dev/null 2>&1 || true
  launchctl bootstrap "gui/$(id -u)" "$plist" || true
  launchctl kickstart -k "gui/$(id -u)/com.smartstock.sync" || true
}

install_backup_launch_agent() {
  if [[ "$(printf '%s' "$MODE" | tr '[:lower:]-' '[:upper:]_')" != "SERVER" ]]; then
    return
  fi
  log "Installing encrypted SmartStock database backups"
  local backup_dir="${HOME}/.smartstock/backups"
  local backup_script="${HOME}/.smartstock/run-smartstock-backup.command"
  local plist="${HOME}/Library/LaunchAgents/com.smartstock.backup.plist"
  mkdir -p "$backup_dir" "${HOME}/Library/LaunchAgents"
  chmod 700 "$backup_dir"
  if ! security find-generic-password -w -s com.smartstock.database -a backup-encryption-key >/dev/null 2>&1; then
    security add-generic-password -U -s com.smartstock.database -a backup-encryption-key -w "$(generate_password)$(generate_password)" >/dev/null
  fi
  cat > "$backup_script" <<EOF
#!/usr/bin/env bash
set -euo pipefail
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:\$PATH"
BACKUP_DIR="\${HOME}/.smartstock/backups"
mkdir -p "\$BACKUP_DIR"
chmod 700 "\$BACKUP_DIR"
DB_USER="\$(security find-generic-password -w -s com.smartstock.database -a primary-db-user)"
DB_PASSWORD="\$(security find-generic-password -w -s com.smartstock.database -a primary-db-password)"
STAMP="\$(date +%Y%m%d-%H%M%S)-\$\$"
TARGET="\$BACKUP_DIR/smartstock-\$STAMP.dump.enc"
PARTIAL="\$TARGET.partial"
trap 'rm -f "\$PARTIAL"' EXIT
PGPASSWORD="\$DB_PASSWORD" pg_dump -h 127.0.0.1 -p ${DB_PORT} -U "\$DB_USER" -d ${DB_NAME} -Fc |
  openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 \
    -pass file:<(security find-generic-password -w -s com.smartstock.database -a backup-encryption-key) \
    -out "\$PARTIAL"
mv "\$PARTIAL" "\$TARGET"
trap - EXIT
chmod 600 "\$TARGET"
find "\$BACKUP_DIR" -type f -name 'smartstock-*.dump.enc' -mtime +30 -delete
EOF
  chmod 700 "$backup_script"
  cat > "$plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>com.smartstock.backup</string>
  <key>ProgramArguments</key><array><string>${backup_script}</string></array>
  <key>RunAtLoad</key><true/>
  <key>StartInterval</key><integer>21600</integer>
  <key>StandardOutPath</key><string>${HOME}/.smartstock/backup.log</string>
  <key>StandardErrorPath</key><string>${HOME}/.smartstock/backup.err.log</string>
</dict>
</plist>
EOF
  launchctl bootout "gui/$(id -u)" "$plist" >/dev/null 2>&1 || true
  launchctl bootstrap "gui/$(id -u)" "$plist" || true
  launchctl kickstart -k "gui/$(id -u)/com.smartstock.backup" || true
}

backup_existing_files
if [[ "$(printf '%s' "$MODE" | tr '[:lower:]-' '[:upper:]_')" == "SERVER" ]]; then
  load_or_create_credentials
  install_dependencies
  start_postgres
  reset_database_if_requested
  init_database
  restrict_postgres_to_server
  write_config
  write_credentials_note
else
  install_client_dependencies
  write_client_config
fi
build_app
write_launchers
install_sync_launch_agent
install_backup_launch_agent

log "SmartStock installation complete"
printf 'Install mode: %s\n' "$MODE"
printf 'Existing config/credential backups use suffix: %s\n' "$BACKUP_SUFFIX"
printf 'Config saved to: %s\n' "$CONFIG_PATH"
printf 'Client launcher: %s\n' "${APP_DIR}/target/run-smartstock-client.command"
if [[ "$(printf '%s' "$MODE" | tr '[:lower:]-' '[:upper:]_')" == "SERVER" ]]; then
  printf 'Credentials saved to: %s\n' "$CREDENTIALS_PATH"
  printf 'Server DB user: %s\n' "$DB_USER"
  printf 'Server launcher: %s\n' "${APP_DIR}/target/run-smartstock-server.command"
  printf 'Sync service launcher: %s\n' "${HOME}/.smartstock/sync-service/run-smartstock-sync-service.command"
  printf 'Encrypted backups: %s\n' "${HOME}/.smartstock/backups"
fi
