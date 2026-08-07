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
MAC_DARK_ICON_PATH="$WORK_DIR/${APP_NAME}Dark.icns"
UPDATER_LAUNCHER_PROPERTIES="$WORK_DIR/updater-launcher.properties"

clear_extended_attributes() {
  local target="$1"
  if [[ -e "$target" ]]; then
    find "$target" -exec xattr -c {} + 2>/dev/null || true
  fi
}

generate_source_icons() {
  local classes_dir="$WORK_DIR/icon-generator-classes"
  if ! command -v javac >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then
    echo "Missing javac or java, which are required to generate SmartStock app icons." >&2
    exit 1
  fi

  mkdir -p "$classes_dir"
  javac -d "$classes_dir" "$ROOT_DIR/tools/GenerateAppIcons.java"
  java -cp "$classes_dir" GenerateAppIcons "$ROOT_DIR/src/Images"
}

build_icns_from_png() {
  local source_png="$1"
  local output_icns="$2"
  local icon_png_dir="$WORK_DIR/$(basename "$output_icns" .icns)-pngs"

  if [[ ! -f "$source_png" ]]; then
    echo "Missing icon source: $source_png" >&2
    exit 1
  fi
  if ! command -v sips >/dev/null 2>&1 || ! command -v python3 >/dev/null 2>&1; then
    echo "Missing sips or python3, which are required to build the macOS app icon." >&2
    exit 1
  fi

  mkdir -p "$icon_png_dir"
  for size in 16 32 128 256 512 1024; do
    sips -z "$size" "$size" "$source_png" --out "$icon_png_dir/icon_${size}.png" >/dev/null
  done

  python3 - "$icon_png_dir" "$output_icns" <<'PY'
import pathlib
import struct
import sys

source_dir = pathlib.Path(sys.argv[1])
output_path = pathlib.Path(sys.argv[2])
chunks = [
    ("ic08", "icon_256.png"),
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

build_macos_icons() {
  if [[ "${FORCE_GENERATE_APP_ICONS:-0}" == "1" || ! -f "$ROOT_DIR/src/Images/AppIconLight.png" || ! -f "$ROOT_DIR/src/Images/AppIconDark.png" ]]; then
    generate_source_icons
  fi
  build_icns_from_png "$ROOT_DIR/src/Images/AppIconLight.png" "$MAC_ICON_PATH"
  build_icns_from_png "$ROOT_DIR/src/Images/AppIconDark.png" "$MAC_DARK_ICON_PATH"
}

cd "$ROOT_DIR"
"$ROOT_DIR/tools/lan-api-cutover-check.sh"
"$ROOT_DIR/tools/security-check.sh"
# Release bundles must be built from a clean output directory. A plain
# `mvn package` can retain resources that were renamed or removed from src,
# silently bloating the updater archive with stale files.
mvn clean package

JAR_PATH="$(ls -t "$TARGET_DIR"/inventory-management-*.jar | head -n 1)"
JAR_NAME="$(basename "$JAR_PATH")"
if jar tf "$JAR_PATH" | rg -q '(^|/)(\.env|database-credentials\.txt|database\.properties)$'; then
  echo "Refusing to package privileged configuration or credential files." >&2
  exit 1
fi
VERSION="${JAR_NAME#inventory-management-}"
VERSION="${VERSION%.jar}"
IFS='.' read -r VERSION_MAJOR VERSION_MINOR VERSION_PATCH <<< "$VERSION"
BUILD_NUMBER=$((10#$VERSION_MAJOR * 10000 + 10#$VERSION_MINOR * 100 + 10#$VERSION_PATCH))
ZIP_PATH="$RELEASE_DIR/smartstock-mac-$VERSION.zip"
DMG_PATH="$RELEASE_DIR/smartstock-mac-$VERSION.dmg"

rm -rf "$RELEASE_DIR"
trap 'rm -rf "$WORK_DIR"' EXIT
mkdir -p "$RELEASE_DIR"
mkdir -p "$JPACKAGE_INPUT_DIR/dependency"

cp "$JAR_PATH" "$JPACKAGE_INPUT_DIR/"
cp -R "$TARGET_DIR/dependency/." "$JPACKAGE_INPUT_DIR/dependency/"
# These artifacts contain compile-time annotations only. They are not referenced
# by SmartStock's runtime dependency graph and needlessly inflate updater bundles.
rm -f "$JPACKAGE_INPUT_DIR/dependency/checker-qual-"*.jar
rm -f "$JPACKAGE_INPUT_DIR/dependency/error_prone_annotations-"*.jar
build_macos_icons
cat > "$UPDATER_LAUNCHER_PROPERTIES" <<EOF
main-jar=$JAR_NAME
main-class=app.SmartStockUpdater
EOF

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
  --add-launcher "SmartStockUpdater=$UPDATER_LAUNCHER_PROPERTIES" \
  --add-modules "java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.security.jgss,java.security.sasl,java.smartcardio,java.sql,java.transaction.xa,java.xml,jdk.crypto.ec,jdk.httpserver,jdk.unsupported" \
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
cp "$MAC_DARK_ICON_PATH" "$APP_BUNDLE/Contents/Resources/${APP_NAME}Dark.icns"
clear_extended_attributes "$APP_BUNDLE"
codesign --force --deep --sign - "$APP_BUNDLE"
codesign --verify --deep --strict --verbose=2 "$APP_BUNDLE"

# The currently installed updater extracts this archive with ditto. Excluding
# resource forks and extended attributes prevents FinderInfo/AppleDouble data
# from being restored onto the staged bundle and invalidating its signature.
ditto -c -k --keepParent --norsrc --noextattr --noqtn --noacl "$APP_BUNDLE" "$ZIP_PATH"

if command -v hdiutil >/dev/null 2>&1; then
  rm -rf "$DMG_STAGING_DIR"
  mkdir -p "$DMG_STAGING_DIR"
  cp -R "$APP_BUNDLE" "$DMG_STAGING_DIR/"
  ln -s /Applications "$DMG_STAGING_DIR/Applications"
  cat > "$DMG_STAGING_DIR/Install SmartStock.command" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_APP="$SCRIPT_DIR/SmartStock.app"
TARGET_APP="/Applications/SmartStock.app"

if [[ ! -d "$SOURCE_APP" ]]; then
  echo "SmartStock.app was not found next to this installer."
  exit 1
fi

echo "Installing SmartStock to /Applications..."
if [[ -d "$TARGET_APP" ]]; then
  sudo rm -rf "$TARGET_APP"
fi
sudo ditto "$SOURCE_APP" "$TARGET_APP"

echo "Clearing download quarantine metadata..."
sudo xattr -rc "$TARGET_APP" 2>/dev/null || true

echo "Applying local personal-use signature..."
sudo codesign --force --deep --sign - "$TARGET_APP"
sudo codesign --verify --deep --strict --verbose=2 "$TARGET_APP"

echo
echo "SmartStock installed successfully."
echo "You can open it from /Applications now."
read -r -p "Press Return to close this window. " _
EOF
  chmod +x "$DMG_STAGING_DIR/Install SmartStock.command"
  cat > "$DMG_STAGING_DIR/INSTALL.txt" <<EOF
Install SmartStock

For personal/internal installs, open Terminal and run:
  bash "/Volumes/SmartStock/Install SmartStock.command"

If macOS allows it, you can also right-click Install SmartStock.command and
choose Open.

That installer copies SmartStock.app to Applications, clears quarantine metadata,
and applies a local ad-hoc signature for this Mac.

Manual install:
  1. Drag SmartStock.app onto Applications.
  2. Run:
     sudo xattr -rc /Applications/SmartStock.app
     sudo codesign --force --deep --sign - /Applications/SmartStock.app

If macOS says the app cannot be verified, SmartStock has not been signed and
notarized yet. For internal testing, right-click SmartStock.app and choose Open.
For customer distribution, run tools/notarize-macos-release.sh with an Apple
Developer ID Application certificate.
EOF
  if ! hdiutil create -volname "$APP_NAME" -srcfolder "$DMG_STAGING_DIR" -ov -format UDZO "$DMG_PATH" >/dev/null; then
    printf 'Warning: DMG creation failed; continuing with the zipped app release artifact.\n' >&2
    rm -f "$DMG_PATH"
  fi
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
  '$VERSION', $BUILD_NUMBER, 'mac', 'smartstock-releases', 'mac/smartstock-mac-$VERSION.zip',
  '$SHA256', $SIZE_BYTES, 'SmartStock $VERSION Mac update.', false, true, now()
);
EOF

if [[ -f "$DMG_PATH" ]]; then
  printf '\nBootstrap DMG: %s\n' "$DMG_PATH"
fi

if [[ -d "$APP_BUNDLE/Contents/runtime" ]]; then
  printf 'Bundled runtime: %s\n' "$APP_BUNDLE/Contents/runtime"
fi
