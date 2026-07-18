#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_DIR="$ROOT_DIR/target"
RELEASE_DIR="$TARGET_DIR/release"

cd "$ROOT_DIR"
"$ROOT_DIR/tools/lan-api-cutover-check.sh"
"$ROOT_DIR/tools/security-check.sh"
mvn package

JAR_PATH="$(ls -t "$TARGET_DIR"/inventory-management-*.jar | head -n 1)"
JAR_NAME="$(basename "$JAR_PATH")"
if jar tf "$JAR_PATH" | rg -q '(^|/)(\.env|database-credentials\.txt|database\.properties)$'; then
  echo "Refusing to package privileged configuration or credential files." >&2
  exit 1
fi
VERSION="${JAR_NAME#inventory-management-}"
VERSION="${VERSION%.jar}"
ZIP_PATH="$RELEASE_DIR/smartstock-windows-$VERSION.zip"

rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR/payload/dependency"
cp "$JAR_PATH" "$RELEASE_DIR/payload/"
cp -R "$TARGET_DIR/dependency/." "$RELEASE_DIR/payload/dependency/"
cat > "$RELEASE_DIR/payload/run-smartstock-client.cmd" <<EOF
@echo off
cd /d "%~dp0"
java -jar "$JAR_NAME" --client
EOF
cat > "$RELEASE_DIR/payload/INSTALL.txt" <<'EOF'
SmartStock Register

1. Install Java 17 or later on this Windows register.
2. Run run-smartstock-client.cmd.
3. An administrator pairs the register once. Employees then use the normal login.

This register package contains no PostgreSQL or cloud database credential.
EOF

(
  cd "$RELEASE_DIR/payload"
  zip -qr "$ZIP_PATH" "$JAR_NAME" dependency run-smartstock-client.cmd INSTALL.txt
)

SHA256="$(shasum -a 256 "$ZIP_PATH" | awk '{print $1}')"
SIZE_BYTES="$(wc -c < "$ZIP_PATH" | tr -d ' ')"

cat <<EOF
Release artifact: $ZIP_PATH
Version: $VERSION
SHA-256: $SHA256
Size bytes: $SIZE_BYTES

Supabase metadata example:
insert into app_releases (
  version, build_number, platform, artifact_bucket, artifact_path,
  sha256, file_size_bytes, release_notes, required, published, published_at
) values (
  '$VERSION', 10000, 'windows', 'smartstock-releases', 'windows/smartstock-windows-$VERSION.zip',
  '$SHA256', $SIZE_BYTES, 'SmartStock $VERSION update.', false, true, now()
);
EOF
