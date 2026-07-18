#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_APP="$SCRIPT_DIR/SmartStock.app"
TARGET_APP="/Applications/SmartStock.app"

if [[ ! -d "$SOURCE_APP" ]]; then
  echo "SmartStock.app was not found beside this installer."
  read -r -p "Press Return to close. " _
  exit 1
fi

echo "SmartStock Internal Test Installer"
echo "This will install SmartStock in /Applications."
echo

if [[ -d "$TARGET_APP" ]]; then
  sudo rm -rf "$TARGET_APP"
fi

sudo ditto "$SOURCE_APP" "$TARGET_APP"
sudo xattr -cr "$TARGET_APP"
sudo codesign --force --deep --sign - "$TARGET_APP"
sudo codesign --verify --deep --strict "$TARGET_APP"

echo
echo "SmartStock was installed successfully."
open "$TARGET_APP"
read -r -p "Press Return to close. " _
