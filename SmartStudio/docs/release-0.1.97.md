# SmartStudio 0.1.97 Windows quick update

Build 1097, prepared 4 November 2026 with the exact SmartStock 1.1.104 client dependency.

Fixes business-card artwork clipped on all four edges when the native print
dialog supplies an inherited one-inch printable margin. Resolve the printable
area from the selected printer and final media settings instead. Preserves
actual card dimensions, original-resolution artwork and the confirmed Canon
Windows raster-printing fix.

- Updater: `SmartStudio-update-0.1.97.exe`, 173281519 bytes.
- SHA-256: `d53ddfa0fd70ba81a0f605b7c12cdf2aba288e737d368a8f0355d365e90488e0`.
- Full Maven test/package: 346 tests, zero failures/errors, one skipped.
- Security and whitespace checks passed; hosted model catalogue verified.
- Standard update-only packaging and native launch passed. Exact updater
  installed over an isolated 0.1.96 copy; exact JAR identities, preserved runtime,
  excluded model weights, retained runtime/notices and installed native launch
  passed. Existing shortcuts restored byte-for-byte.

Physical C5240 output remains a separate acceptance check. No live installation
or restart was performed. First-install selection remains unchanged.

Published 4 November 2026 after complete hosted download, size and SHA-256
verification. Independent metadata read-back confirmed Windows version 0.1.97,
build 1097, published status, artifact path, size and hash.
