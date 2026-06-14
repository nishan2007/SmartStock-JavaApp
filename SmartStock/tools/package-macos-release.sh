#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_DIR="$ROOT_DIR/target"
RELEASE_DIR="$TARGET_DIR/release-mac"
APP_NAME="SmartStock"
WORK_DIR="$(mktemp -d /tmp/smartstock-mac-release.XXXXXX)"
APP_BUNDLE="$WORK_DIR/${APP_NAME}.app"
DMG_STAGING_DIR="$WORK_DIR/dmg-root"
JPACKAGE_INPUT_DIR="$WORK_DIR/jpackage-input"
MAC_ICON_PATH="$WORK_DIR/${APP_NAME}.icns"

clear_extended_attributes() {
  local target="$1"
  if [[ -e "$target" ]]; then
    find "$target" -exec xattr -c {} + 2>/dev/null || true
  fi
}

build_macos_icon() {
  local source_png="$ROOT_DIR/src/Images/CenterLogo.png"
  local base_png="$WORK_DIR/${APP_NAME}-icon-base.png"
  local padded_png="$WORK_DIR/${APP_NAME}-icon-padded.png"
  local icon_png_dir="$WORK_DIR/${APP_NAME}-icon-pngs"

  if [[ ! -f "$source_png" ]]; then
    echo "Missing icon source: $source_png" >&2
    exit 1
  fi
  if ! command -v sips >/dev/null 2>&1 || ! command -v python3 >/dev/null 2>&1; then
    echo "Missing sips or python3, which are required to build the macOS app icon." >&2
    exit 1
  fi

  mkdir -p "$icon_png_dir"
  sips -Z 900 "$source_png" --out "$base_png" >/dev/null
  sips --padToHeightWidth 1024 1024 --padColor FFFFFF "$base_png" --out "$padded_png" >/dev/null

  for size in 16 32 128 256 512 1024; do
    sips -z "$size" "$size" "$padded_png" --out "$icon_png_dir/icon_${size}.png" >/dev/null
  done

  python3 - "$icon_png_dir" "$MAC_ICON_PATH" <<'PY'
import pathlib
import struct
import sys

source_dir = pathlib.Path(sys.argv[1])
output_path = pathlib.Path(sys.argv[2])
chunks = [
    ("ic04", "icon_16.png"),
    ("ic05", "icon_32.png"),
    ("ic07", "icon_128.png"),
    ("ic08", "icon_256.png"),
    ("ic09", "icon_512.png"),
    ("ic10", "icon_1024.png"),
]
body = bytearray()
for chunk_type, filename in chunks:
    data = (source_dir / filename).read_bytes()
    body.extend(chunk_type.encode("ascii"))
    body.extend(struct.pack(">I", len(data) + 8))
    body.extend(data)
output_path.write_bytes(b"icns" + struct.pack(">I", len(body) + 8) + body)
PY
}

cd "$ROOT_DIR"
mvn package -DskipTests

JAR_PATH="$(ls -t "$TARGET_DIR"/inventory-management-*.jar | head -n 1)"
JAR_NAME="$(basename "$JAR_PATH")"
VERSION="${JAR_NAME#inventory-management-}"
VERSION="${VERSION%.jar}"
ZIP_PATH="$RELEASE_DIR/smartstock-mac-$VERSION.zip"
DMG_PATH="$RELEASE_DIR/smartstock-mac-$VERSION.dmg"

rm -rf "$RELEASE_DIR"
trap 'rm -rf "$WORK_DIR"' EXIT
mkdir -p "$RELEASE_DIR"
mkdir -p "$JPACKAGE_INPUT_DIR/dependency"

cp "$JAR_PATH" "$JPACKAGE_INPUT_DIR/"
cp -R "$TARGET_DIR/dependency/." "$JPACKAGE_INPUT_DIR/dependency/"
build_macos_icon

if ! command -v jpackage >/dev/null 2>&1; then
  cat >&2 <<EOF
Missing jpackage.

Install a full JDK, not just a JRE, then rerun this script. On Apple Silicon:
  brew install openjdk@17
EOF
  exit 1
fi

set +e
JPACKAGE_OUTPUT="$(jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --input "$JPACKAGE_INPUT_DIR" \
  --main-jar "$JAR_NAME" \
  --main-class app.Main \
  --dest "$WORK_DIR" \
  --app-version "$VERSION" \
  --icon "$MAC_ICON_PATH" \
  --mac-package-identifier "com.smartstock.desktop" \
  --java-options "-Dapple.laf.useScreenMenuBar=true" 2>&1)"
JPACKAGE_STATUS=$?
set -e
if [[ $JPACKAGE_STATUS -ne 0 ]]; then
  if [[ -d "$APP_BUNDLE" && "$JPACKAGE_OUTPUT" == *"codesign"* ]]; then
    printf '%s\n' "$JPACKAGE_OUTPUT" >&2
    printf 'jpackage created the app image but codesign rejected extended attributes. Cleaning and ad-hoc signing locally.\n' >&2
    clear_extended_attributes "$APP_BUNDLE"
    codesign --force --deep --sign - "$APP_BUNDLE"
  else
    printf '%s\n' "$JPACKAGE_OUTPUT" >&2
    exit "$JPACKAGE_STATUS"
  fi
fi

if [[ ! -d "$APP_BUNDLE/Contents/runtime" ]]; then
  echo "SmartStock.app was created without a bundled runtime." >&2
  exit 1
fi
clear_extended_attributes "$APP_BUNDLE"

(
  cd "$WORK_DIR"
  zip -qr "$ZIP_PATH" "${APP_NAME}.app"
)

if command -v hdiutil >/dev/null 2>&1; then
  rm -rf "$DMG_STAGING_DIR"
  mkdir -p "$DMG_STAGING_DIR"
  cp -R "$APP_BUNDLE" "$DMG_STAGING_DIR/"
  ln -s /Applications "$DMG_STAGING_DIR/Applications"
  cat > "$DMG_STAGING_DIR/INSTALL.txt" <<EOF
Install SmartStock

Drag SmartStock.app onto Applications.

If macOS says the app cannot be verified, SmartStock has not been signed and
notarized yet. For internal testing, right-click SmartStock.app and choose Open.
For customer distribution, run tools/notarize-macos-release.sh with an Apple
Developer ID Application certificate.
EOF
  hdiutil create -volname "$APP_NAME" -srcfolder "$DMG_STAGING_DIR" -ov -format UDZO "$DMG_PATH" >/dev/null
fi

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
  '$VERSION', 10000, 'mac', 'smartstock-releases', 'mac/smartstock-mac-$VERSION.zip',
  '$SHA256', $SIZE_BYTES, 'SmartStock $VERSION Mac update.', false, true, now()
);
EOF

if [[ -f "$DMG_PATH" ]]; then
  printf '\nBootstrap DMG: %s\n' "$DMG_PATH"
fi

if [[ -d "$APP_BUNDLE/Contents/runtime" ]]; then
  printf 'Bundled runtime: %s\n' "$APP_BUNDLE/Contents/runtime"
fi
