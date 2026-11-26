# SmartStudio 0.1.95 Windows quick update

Build 1095, prepared 3 November 2026 with the current SmartStock 1.1.104
client dependency. Includes the cumulative shared workspace changes from 0.1.94.

Badge artwork now rotates by 90 degrees when its physical dimensions fit the
driver's printable area only in the opposite orientation. Front and back use
the same logic; the badge is not shrunk to conceal a paper-size mismatch.

- Updater: `SmartStudio-update-0.1.95.exe`, 173281238 bytes.
- SHA-256: `cf36b653101221f0f26ce3c4a4b1a72f70f3eb399f8713ce65ceb7143a914bc5`.
- Full SmartStudio Maven suite: 345 tests, zero failures/errors, one skipped.
- Repository security check and changed-file whitespace check passed.
- Standard Windows update-only packaging and native launcher check passed.
- Exact updater installed over an isolated 0.1.94 app image. Installed Studio
  and SmartStock JAR hashes matched the build. Native launch, preservation of
  the existing runtime executable and absence of loose model weights passed.
- Existing desktop and Start Menu shortcuts were preserved and restored after
  isolated installation. The live application was not installed or restarted.

Physical Magicard 300 printing, paired-store behavior and macOS verification
remain outstanding. This release does not change the public first-install
selection or model catalogue.

Published 3 November 2026 after a complete hosted download verified the exact
173281238 bytes and SHA-256 above. Independent metadata read-back confirmed
version 0.1.95, build 1095, Windows platform, published status, object path,
size and hash.
