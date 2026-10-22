# SmartStudio 0.1.46 / build 1046

Adds visible New template buttons above the Badge Cards, Business Cards and
Labels editor toolbars, plus direct create buttons in their template choosers.
Existing unsaved-change prompts remain. A separate action row keeps zoom and
print controls visible at smaller window sizes.

Uses SmartStock 1.1.52 client libraries. The store server needs SmartStock 1.1.52
for the staff template-save permission fix. No model changes are included.

Validation: all 153 SmartStudio tests passed, with no failures, errors or skips.
Repository security and whitespace checks passed. Hosted model catalogue ready.
Portable and exact extracted-installer native launchers passed startup checks;
login preview and the small-window badge editor render were visually inspected.
Extracted application and client JAR hashes match the build. All 41 packaged
JARs and loose files were checked for model weights; none were included.
ONNX Runtime 1.23.2 remains packaged.

Installer: `SmartStudio-setup-0.1.46.exe`, 189645590 bytes,
SHA-256 `6414cf7e4325f99d50cc753d223880571b6457e5c3221898fda355bdf94c747e`.

Portable: `SmartStudio-0.1.46-windows-20261019-102317.zip`, 192032102 bytes,
SHA-256 `0a0ea3b18c9471ba445d296a032b68c60ccfb241ea3f80bb9f04eadbdb65919f`.

Live staff sign-in, creation/save against a store, physical printer/Risograph,
drawer/NFC, live database, Windows service, full installed update/rollback and
model migration checks remain separate and outstanding. No running installation
was changed. No macOS package is part of this release.

Published on 2026-10-19 after the complete hosted installer download matched the
expected size and SHA-256. Independent release metadata read-back matched version,
build, published status, object path, size and SHA-256. The protected portal
configuration was backed up and its Windows Studio selection updated while
preserving other selections. The full public download also matched the expected
filename, 189645590 bytes and SHA-256.

Public download: https://downloads.deckers.gy/download/smartstudio/windows

Portal deployment: `4d33dddb-4fca-44fc-bd72-2ed6e80f991b`.
