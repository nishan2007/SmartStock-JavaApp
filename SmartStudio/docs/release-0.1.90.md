# SmartStudio 0.1.90 Windows quick update

Build 1090, prepared 28 October 2026. Frozen source and evidence are retained at
D:/SmartStudio-release-0.1.90. Includes all current shared Studio source changes
and the exact verified SmartStock 1.1.93 client dependency.

Template rename updates both the local filename and stored template name.
Shared renames retain the template identity and wait for server confirmation.
Unrelated draft edits remain unsaved; conflict and failed preparation preserve
the original file. File backstage refreshes and confirms completed renames.

Badge Cards now exposes Add barcode and Add QR code in Insert > Codes.
Codes are stored as portable embedded artwork and decode after save/reopen.

- Updater: SmartStudio-update-0.1.90.exe, 173,069,997 bytes.
- SHA-256: d60d127592835ebccd2e4cd924813adf72ce498615121136907a9625f4d1ee4a.
- Full Maven suite: 344 tests, zero failures/errors, one skipped.
- Security and whitespace checks passed. All 477 frozen Studio files matched
  the shared workspace before packaging.
- Isolated 0.1.89-to-0.1.90 update, Java runtime preservation, exact installed
  application/core JAR identity and native startup passed.
- Extracted updater excludes Java runtime and model weights; inference runtime
  and required notices remain present.

Quick-update feed only. No first-install selection, macOS release or live
installation. Live paired rename and physical barcode scanning/printing remain
separate acceptance checks. Save drafts before restarting SmartStudio.

Published 28 October 2026 after full hosted download, size and SHA-256
verification. Independent metadata read-back confirmed version 0.1.90, build
1090, Windows platform, published status, exact object path, size and hash.
