#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
MODE="server"
RESET_LOCAL_DB="no"
for arg in "$@"; do
  case "$arg" in
    server|client|cloud-direct|cloud_direct)
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
CLIENT_DB_USER="${SMARTSTOCK_CLIENT_DB_USER:-smartstock_client}"
CLIENT_DB_PASSWORD="${SMARTSTOCK_CLIENT_DB_PASSWORD:-SmartStockClientLan2026!}"
DB_PORT="${SMARTSTOCK_DB_PORT:-5432}"
SYNC_INTERVAL="${SMARTSTOCK_SYNC_INTERVAL:-60}"
CLOUD_JDBC_URL="${SMARTSTOCK_CLOUD_JDBC_URL:-}"
CLOUD_DB_USER="${SMARTSTOCK_CLOUD_DB_USER:-}"
CLOUD_DB_PASSWORD="${SMARTSTOCK_CLOUD_DB_PASSWORD:-}"
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

properties_get() {
  local key="$1"
  local file="$2"
  awk -F= -v wanted="$key" '
    $0 !~ /^[[:space:]]*#/ && $1 == wanted {
      sub(/^[^=]*=/, "")
      print
      exit
    }
  ' "$file" | tr -d '\r'
}

load_existing_cloud_config() {
  if [[ ! -f "$CONFIG_PATH" ]]; then
    return
  fi
  if [[ -z "$CLOUD_JDBC_URL" ]]; then
    CLOUD_JDBC_URL="$(properties_get "cloud.jdbc.url" "$CONFIG_PATH")"
  fi
  if [[ -z "$CLOUD_DB_USER" ]]; then
    CLOUD_DB_USER="$(properties_get "cloud.db.user" "$CONFIG_PATH")"
  fi
  if [[ -z "$CLOUD_DB_PASSWORD" ]]; then
    CLOUD_DB_PASSWORD="$(properties_get "cloud.db.password" "$CONFIG_PATH")"
  fi
}

load_or_create_credentials() {
  mkdir -p "${HOME}/.smartstock"
  if [[ -f "$CREDENTIALS_PATH" ]]; then
    # shellcheck disable=SC1090
    source "$CREDENTIALS_PATH"
    DB_USER="${SMARTSTOCK_DB_USER:-$DB_USER}"
    DB_PASSWORD="${SMARTSTOCK_DB_PASSWORD:-$DB_PASSWORD}"
    CLIENT_DB_USER="${SMARTSTOCK_CLIENT_DB_USER:-$CLIENT_DB_USER}"
    CLIENT_DB_PASSWORD="${SMARTSTOCK_CLIENT_DB_PASSWORD:-$CLIENT_DB_PASSWORD}"
  fi
  DB_USER="$(clean_placeholder "$DB_USER")"
  DB_PASSWORD="$(clean_placeholder "$DB_PASSWORD")"
  CLIENT_DB_USER="$(clean_placeholder "$CLIENT_DB_USER")"
  CLIENT_DB_PASSWORD="$(clean_placeholder "$CLIENT_DB_PASSWORD")"
  if [[ -z "$DB_USER" ]]; then
    DB_USER="smartstock_server"
  fi
  if [[ -z "$CLIENT_DB_USER" ]]; then
    CLIENT_DB_USER="smartstock_client"
  fi
  if [[ -z "$DB_PASSWORD" ]]; then
    DB_PASSWORD="$(generate_password)"
  fi
  if [[ -z "$CLIENT_DB_PASSWORD" ]]; then
    CLIENT_DB_PASSWORD="SmartStockClientLan2026!"
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
    -v db_password="$DB_PASSWORD" \
    -v client_db_user="$CLIENT_DB_USER" \
    -v client_db_password="$CLIENT_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_user', :'db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_user')\gexec

SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'db_user', :'db_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_user')\gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'client_db_user', :'client_db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'client_db_user')\gexec

SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'client_db_user', :'client_db_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'client_db_user')\gexec

SELECT 'CREATE ROLE service_role NOLOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role')\gexec

SELECT 'CREATE ROLE authenticated NOLOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated')\gexec

SELECT 'CREATE ROLE anon NOLOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'db_name', :'db_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db_name')\gexec

SELECT format('GRANT ALL PRIVILEGES ON DATABASE %I TO %I', :'db_name', :'db_user')\gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'db_name', :'client_db_user')\gexec
SQL

  psql "postgresql://${DB_USER}:${DB_PASSWORD}@127.0.0.1:${DB_PORT}/${DB_NAME}" \
    -v ON_ERROR_STOP=1 \
    -f "${APP_DIR}/database/base_schema_setup.sql"

  psql "postgresql://${DB_USER}:${DB_PASSWORD}@127.0.0.1:${DB_PORT}/${DB_NAME}" \
    -v ON_ERROR_STOP=1 \
    -f "${APP_DIR}/database/local_network_sync_setup.sql"

  apply_feature_schema_scripts

  psql "postgresql://${DB_USER}:${DB_PASSWORD}@127.0.0.1:${DB_PORT}/${DB_NAME}" \
    -v ON_ERROR_STOP=1 \
    -v client_db_user="$CLIENT_DB_USER" <<'SQL'
GRANT USAGE ON SCHEMA public TO :"client_db_user";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"client_db_user";
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO :"client_db_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"client_db_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO :"client_db_user";
SQL
}

apply_feature_schema_scripts() {
  log "Applying SmartStock feature schema scripts"
  local scripts=(
    permission_descriptions_setup.sql
    permission_descriptions_and_sections_backfill.sql
    location_management_setup.sql
    store_timezone_setup.sql
    department_setup.sql
    vendor_setup.sql
    held_cart_setup.sql
    product_type_setup.sql
    product_size_setup.sql
    product_sku_setup.sql
    customer_type_setup.sql
    device_management_setup.sql
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

write_config() {
  log "Writing SmartStock database config"
  mkdir -p "${HOME}/.smartstock"
  cat > "$CONFIG_PATH" <<EOF
# SmartStock database mode and sync configuration
mode=$(printf '%s' "$MODE" | tr '[:lower:]-' '[:upper:]_')
server.host=127.0.0.1
server.port=${DB_PORT}
database.name=${DB_NAME}
jdbc.url=jdbc:postgresql://127.0.0.1:${DB_PORT}/${DB_NAME}
db.user=${DB_USER}
db.password=${DB_PASSWORD}
sync.interval.seconds=${SYNC_INTERVAL}
EOF
  if [[ -n "$CLOUD_JDBC_URL" || -n "$CLOUD_DB_USER" || -n "$CLOUD_DB_PASSWORD" ]]; then
    cat >> "$CONFIG_PATH" <<EOF
cloud.jdbc.url=${CLOUD_JDBC_URL}
cloud.db.user=${CLOUD_DB_USER}
cloud.db.password=${CLOUD_DB_PASSWORD}
EOF
  fi
  chmod 600 "$CONFIG_PATH"
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

# Use these on the dedicated server computer.
SMARTSTOCK_DB_USER='${DB_USER}'
SMARTSTOCK_DB_PASSWORD='${DB_PASSWORD}'
SMARTSTOCK_SERVER_JDBC_URL='jdbc:postgresql://127.0.0.1:${DB_PORT}/${DB_NAME}'

# Use these on register/client computers.
SMARTSTOCK_CLIENT_DB_USER='${CLIENT_DB_USER}'
SMARTSTOCK_CLIENT_DB_PASSWORD='${CLIENT_DB_PASSWORD}'
SMARTSTOCK_CLIENT_JDBC_URL='jdbc:postgresql://${server_host}:${DB_PORT}/${DB_NAME}'
EOF
  chmod 600 "$CREDENTIALS_PATH"
}

build_app() {
  log "Building SmartStock"
  cd "$APP_DIR"
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
  cat > "${APP_DIR}/target/run-smartstock-server.command" <<EOF
#!/usr/bin/env bash
cd "${APP_DIR}"
java -jar target/${jar_name} --server
EOF
  chmod +x "${APP_DIR}/target/run-smartstock-server.command"

  cat > "${APP_DIR}/target/run-smartstock-client.command" <<EOF
#!/usr/bin/env bash
cd "${APP_DIR}"
java -jar target/${jar_name} --client
EOF
  chmod +x "${APP_DIR}/target/run-smartstock-client.command"

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
exec java -jar ${jar_name} --sync-service
EOF
  chmod +x "${service_dir}/run-smartstock-sync-service.command"
}

install_sync_launch_agent() {
  if [[ "$(printf '%s' "$MODE" | tr '[:lower:]-' '[:upper:]_')" != "SERVER" ]]; then
    return
  fi
  log "Installing SmartStock background sync LaunchAgent"
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

load_existing_cloud_config
load_or_create_credentials
backup_existing_files
install_dependencies
start_postgres
reset_database_if_requested
init_database
write_config
write_credentials_note
build_app
write_launchers
install_sync_launch_agent

log "SmartStock installation complete"
printf 'Install mode: %s\n' "$MODE"
printf 'Existing config/credential backups use suffix: %s\n' "$BACKUP_SUFFIX"
printf 'Credentials saved to: %s\n' "$CREDENTIALS_PATH"
printf 'Config saved to: %s\n' "$CONFIG_PATH"
printf 'Server DB user: %s\n' "$DB_USER"
printf 'Client DB user: %s\n' "$CLIENT_DB_USER"
printf 'Server launcher: %s\n' "${APP_DIR}/target/run-smartstock-server.command"
printf 'Client launcher: %s\n' "${APP_DIR}/target/run-smartstock-client.command"
printf 'Sync service launcher: %s\n' "${HOME}/.smartstock/sync-service/run-smartstock-sync-service.command"
