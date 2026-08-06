#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
TEST_PORT=${SMARTSTOCK_TEST_PG_PORT:-55439}
TEST_CLUSTER=$(mktemp -d "${TMPDIR:-/tmp}/smartstock-server-registry.XXXXXX")

cleanup() {
  if [ -f "$TEST_CLUSTER/postmaster.pid" ]; then
    pg_ctl -D "$TEST_CLUSTER" -m fast stop >/dev/null 2>&1 || true
  fi
  case "$TEST_CLUSTER" in
    /tmp/*|/private/tmp/*|/var/folders/*|/private/var/folders/*) rm -rf -- "$TEST_CLUSTER" ;;
    *) echo "Refusing to remove unexpected test path: $TEST_CLUSTER" >&2 ;;
  esac
}
trap cleanup EXIT INT TERM

for command_name in initdb pg_ctl createdb psql; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "Missing PostgreSQL test command: $command_name" >&2
    exit 1
  }
done

initdb -D "$TEST_CLUSTER" --auth=trust --no-locale >/dev/null
pg_ctl -D "$TEST_CLUSTER" -o "-p $TEST_PORT -c listen_addresses=127.0.0.1" -w start >/dev/null
createdb -h 127.0.0.1 -p "$TEST_PORT" smartstock_registry_test
psql -h 127.0.0.1 -p "$TEST_PORT" -d smartstock_registry_test \
  -f "$PROJECT_DIR/test/sql/store_server_registry_bootstrap.sql" >/dev/null
psql -h 127.0.0.1 -p "$TEST_PORT" -d smartstock_registry_test \
  -f "$PROJECT_DIR/database/migrations/20260806180000_store_server_registry.sql" >/dev/null
psql -h 127.0.0.1 -p "$TEST_PORT" -d smartstock_registry_test \
  -f "$PROJECT_DIR/database/migrations/20260806181000_store_server_registry_indexes.sql" >/dev/null
psql -h 127.0.0.1 -p "$TEST_PORT" -d smartstock_registry_test \
  -f "$PROJECT_DIR/database/migrations/20260806180000_store_server_registry.sql" >/dev/null
psql -h 127.0.0.1 -p "$TEST_PORT" -d smartstock_registry_test \
  -f "$PROJECT_DIR/database/migrations/20260806181000_store_server_registry_indexes.sql" >/dev/null
psql -h 127.0.0.1 -p "$TEST_PORT" -d smartstock_registry_test \
  -f "$PROJECT_DIR/test/sql/store_server_registry_integration.sql"

set +e
psql -h 127.0.0.1 -p "$TEST_PORT" -d smartstock_registry_test -c \
  "SELECT public.smartstock_server_registry('REGISTER_PRIMARY','{\"location_id\":3,\"installation_id\":\"concurrent-a\",\"hostname\":\"concurrent-a\",\"certificate_fingerprint\":\"fp-ca\",\"endpoint_host\":\"concurrent-a.local\"}'::jsonb)" \
  >"$TEST_CLUSTER/concurrent-a.log" 2>&1 &
FIRST_PID=$!
psql -h 127.0.0.1 -p "$TEST_PORT" -d smartstock_registry_test -c \
  "SELECT public.smartstock_server_registry('REGISTER_PRIMARY','{\"location_id\":3,\"installation_id\":\"concurrent-b\",\"hostname\":\"concurrent-b\",\"certificate_fingerprint\":\"fp-cb\",\"endpoint_host\":\"concurrent-b.local\"}'::jsonb)" \
  >"$TEST_CLUSTER/concurrent-b.log" 2>&1 &
SECOND_PID=$!
wait "$FIRST_PID"; FIRST_STATUS=$?
wait "$SECOND_PID"; SECOND_STATUS=$?
set -e

if [ $((FIRST_STATUS + SECOND_STATUS)) -eq 0 ] || [ "$FIRST_STATUS" -ne 0 ] && [ "$SECOND_STATUS" -ne 0 ]; then
  echo "Concurrent primary test expected exactly one successful promotion." >&2
  cat "$TEST_CLUSTER/concurrent-a.log" "$TEST_CLUSTER/concurrent-b.log" >&2
  exit 1
fi

PRIMARY_COUNT=$(psql -h 127.0.0.1 -p "$TEST_PORT" -d smartstock_registry_test -Atc \
  "SELECT count(*) FROM public.store_server_instances WHERE location_id=3 AND role='PRIMARY'")
if [ "$PRIMARY_COUNT" != "1" ]; then
  echo "Concurrent primary test left $PRIMARY_COUNT primaries instead of one." >&2
  exit 1
fi
echo "concurrent primary promotion check passed"
