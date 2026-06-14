#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_DIR="$ROOT_DIR/target"
RELEASE_DIR="$TARGET_DIR/release"

cd "$ROOT_DIR"
mvn package -DskipTests

JAR_PATH="$(ls -t "$TARGET_DIR"/inventory-management-*.jar | head -n 1)"
JAR_NAME="$(basename "$JAR_PATH")"
VERSION="${JAR_NAME#inventory-management-}"
VERSION="${VERSION%.jar}"
ZIP_PATH="$RELEASE_DIR/smartstock-windows-$VERSION.zip"

rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR/payload/dependency"
cp "$JAR_PATH" "$RELEASE_DIR/payload/"
cp -R "$TARGET_DIR/dependency/." "$RELEASE_DIR/payload/dependency/"

(
  cd "$RELEASE_DIR/payload"
  zip -qr "$ZIP_PATH" "$JAR_NAME" dependency
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
