#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RELEASE_DIR="$ROOT_DIR/target/release-mac"
APP_BUNDLE="$RELEASE_DIR/SmartStock.app"

DEVELOPER_ID_APPLICATION="${DEVELOPER_ID_APPLICATION:-}"
DEVELOPER_ID_INSTALLER="${DEVELOPER_ID_INSTALLER:-}"
NOTARY_PROFILE="${NOTARY_PROFILE:-SmartStockNotary}"

usage() {
  cat <<'EOF'
Notarize SmartStock for macOS.

Prerequisites:
  1. Apple Developer Program membership.
  2. Xcode command line tools installed.
  3. A "Developer ID Application" certificate in Keychain.
  4. A notarytool keychain profile, created once with:

     xcrun notarytool store-credentials SmartStockNotary \
       --apple-id you@example.com \
       --team-id YOURTEAMID \
       --password APP-SPECIFIC-PASSWORD

Usage:
  DEVELOPER_ID_APPLICATION="Developer ID Application: Your Name (TEAMID)" \
  bash tools/notarize-macos-release.sh

Optional:
  NOTARY_PROFILE=SmartStockNotary
  DEVELOPER_ID_INSTALLER="Developer ID Installer: Your Name (TEAMID)"
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if [[ -z "$DEVELOPER_ID_APPLICATION" ]]; then
  usage >&2
  echo >&2
  echo "Missing DEVELOPER_ID_APPLICATION." >&2
  exit 1
fi

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_command codesign
require_command xcrun
require_command ditto
require_command hdiutil
require_command shasum

cd "$ROOT_DIR"

if [[ ! -d "$APP_BUNDLE" ]]; then
  echo "Missing $APP_BUNDLE. Run bash tools/package-macos-release.sh first." >&2
  exit 1
fi

JAR_PATH="$(find "$APP_BUNDLE/Contents/app" -maxdepth 1 -name 'inventory-management-*.jar' -type f | sort | tail -n 1)"
if [[ -z "$JAR_PATH" ]]; then
  echo "Missing SmartStock jar inside $APP_BUNDLE." >&2
  exit 1
fi
JAR_NAME="$(basename "$JAR_PATH")"
VERSION="${JAR_NAME#inventory-management-}"
VERSION="${VERSION%.jar}"

ZIP_PATH="$RELEASE_DIR/smartstock-mac-$VERSION.zip"
DMG_PATH="$RELEASE_DIR/smartstock-mac-$VERSION.dmg"
NOTARY_ZIP="$RELEASE_DIR/smartstock-mac-$VERSION.notary.zip"

echo "Signing SmartStock.app"
codesign --force --deep --options runtime --timestamp \
  --sign "$DEVELOPER_ID_APPLICATION" \
  "$APP_BUNDLE"

codesign --verify --deep --strict --verbose=2 "$APP_BUNDLE"
spctl --assess --type execute --verbose=4 "$APP_BUNDLE" || true

echo "Creating notarization zip"
rm -f "$NOTARY_ZIP"
ditto -c -k --keepParent "$APP_BUNDLE" "$NOTARY_ZIP"

echo "Submitting SmartStock.app to Apple notary service"
xcrun notarytool submit "$NOTARY_ZIP" \
  --keychain-profile "$NOTARY_PROFILE" \
  --wait

echo "Stapling notarization ticket to SmartStock.app"
xcrun stapler staple "$APP_BUNDLE"
xcrun stapler validate "$APP_BUNDLE"

echo "Rebuilding signed update zip"
rm -f "$ZIP_PATH"
(
  cd "$RELEASE_DIR"
  zip -qr "$ZIP_PATH" "SmartStock.app"
)

echo "Rebuilding DMG"
rm -f "$DMG_PATH"
hdiutil create -volname "SmartStock" -srcfolder "$APP_BUNDLE" -ov -format UDZO "$DMG_PATH" >/dev/null

if [[ -n "$DEVELOPER_ID_INSTALLER" ]]; then
  echo "Signing DMG"
  codesign --force --timestamp --sign "$DEVELOPER_ID_INSTALLER" "$DMG_PATH"
else
  echo "Signing DMG with Developer ID Application certificate"
  codesign --force --timestamp --sign "$DEVELOPER_ID_APPLICATION" "$DMG_PATH"
fi

echo "Submitting DMG to Apple notary service"
xcrun notarytool submit "$DMG_PATH" \
  --keychain-profile "$NOTARY_PROFILE" \
  --wait

echo "Stapling notarization ticket to DMG"
xcrun stapler staple "$DMG_PATH"
xcrun stapler validate "$DMG_PATH"
spctl --assess --type open --context context:primary-signature --verbose=4 "$DMG_PATH" || true

SHA256="$(shasum -a 256 "$ZIP_PATH" | awk '{print $1}')"
SIZE_BYTES="$(wc -c < "$ZIP_PATH" | tr -d ' ')"

cat <<EOF

Notarized Mac update zip: $ZIP_PATH
Notarized Mac bootstrap DMG: $DMG_PATH
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
