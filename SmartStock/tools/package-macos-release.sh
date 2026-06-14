#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_DIR="$ROOT_DIR/target"
RELEASE_DIR="$TARGET_DIR/release-mac"
APP_NAME="SmartStock"
APP_BUNDLE="$RELEASE_DIR/${APP_NAME}.app"

cd "$ROOT_DIR"
mvn package -DskipTests

JAR_PATH="$(ls -t "$TARGET_DIR"/inventory-management-*.jar | head -n 1)"
JAR_NAME="$(basename "$JAR_PATH")"
VERSION="${JAR_NAME#inventory-management-}"
VERSION="${VERSION%.jar}"
ZIP_PATH="$RELEASE_DIR/smartstock-mac-$VERSION.zip"
DMG_PATH="$RELEASE_DIR/smartstock-mac-$VERSION.dmg"

rm -rf "$RELEASE_DIR"
mkdir -p "$APP_BUNDLE/Contents/MacOS" "$APP_BUNDLE/Contents/app/dependency" "$APP_BUNDLE/Contents/Resources"

cp "$JAR_PATH" "$APP_BUNDLE/Contents/app/"
cp -R "$TARGET_DIR/dependency/." "$APP_BUNDLE/Contents/app/dependency/"

cat > "$APP_BUNDLE/Contents/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key>
  <string>SmartStock</string>
  <key>CFBundleDisplayName</key>
  <string>SmartStock</string>
  <key>CFBundleIdentifier</key>
  <string>com.smartstock.desktop</string>
  <key>CFBundleVersion</key>
  <string>${VERSION}</string>
  <key>CFBundleShortVersionString</key>
  <string>${VERSION}</string>
  <key>CFBundleExecutable</key>
  <string>SmartStock</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>LSMinimumSystemVersion</key>
  <string>12.0</string>
</dict>
</plist>
EOF

cat > "$APP_BUNDLE/Contents/MacOS/SmartStock" <<EOF
#!/usr/bin/env bash
set -euo pipefail
APP_HOME="\$(cd "\$(dirname "\$0")/.." && pwd)"
exec java -jar "\$APP_HOME/app/${JAR_NAME}"
EOF
chmod +x "$APP_BUNDLE/Contents/MacOS/SmartStock"

(
  cd "$RELEASE_DIR"
  zip -qr "$ZIP_PATH" "${APP_NAME}.app"
)

if command -v hdiutil >/dev/null 2>&1; then
  hdiutil create -volname "$APP_NAME" -srcfolder "$APP_BUNDLE" -ov -format UDZO "$DMG_PATH" >/dev/null
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
