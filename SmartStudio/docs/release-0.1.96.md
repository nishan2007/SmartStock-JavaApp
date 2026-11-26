# SmartStudio 0.1.96 Windows quick update

Build 1096, prepared 4 November 2026 with SmartStock 1.1.104 client dependency.

Direct banner printing retains the printer dialog's selected custom media,
accepts larger paper and rotates artwork when only the opposite orientation
fits. Actual artwork size is preserved. Includes the 0.1.95 badge print fix
and cumulative shared workspace changes.

- Updater: `SmartStudio-update-0.1.96.exe`, 173281531 bytes.
- SHA-256: `ced9f11d837cf951b4625cd153b9ec3359c2c7d41e28dc73ee6c872943559ef4`.
- Full Maven suite: 346 tests, zero failures/errors, one skipped.
- Repository security check and changed-file whitespace check passed.
- Standard Windows update-only packaging and native launch passed.
- Exact updater installed over an isolated 0.1.95 image; installed application
  JAR hashes matched, runtime executable was preserved, model weights were
  excluded and installed native startup passed. Existing shortcuts restored.

Physical C4600S banner printing, Magicard printing, paired-store behavior and
macOS checks remain outstanding. Live app files were not installed or restarted.
The public first-install selection and model catalogue are unchanged.

Published 4 November 2026 after the complete hosted download verified the exact
size and SHA-256 above. Independent metadata read-back confirmed version,
build 1096, Windows platform, published status, artifact path, size and hash.
