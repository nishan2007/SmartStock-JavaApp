# SmartStock 1.1.72 Windows release

Prepared 22 October 2026. Build 101072.

This release includes the historical receiving-loss import and simplified
receiving reason selection. The import preserves original dates and audit notes,
does not deduct stock again, excludes existing ledger entries, and labels old
selling values as current-price estimates. See [expense and loss ledgers](expense-loss-ledgers.md).

## Validation

- Standard Windows all-in-one packaging completed with Maven tests: 1,231 tests,
  zero failures/errors, 111 skipped; seven ledger database tests passed.
- Security check and `git diff --check` passed.
- All 890 snapshotted release inputs remained unchanged during the build.
- Exact update ZIP: application JAR identity, historical migration, ledger and
  receiving classes, model-free content and hosted model catalogue verified.
- Exact update native launcher and extracted installer launcher reached the
  welcome window using isolated user profiles. Extracted installer JAR matched
  the accepted application JAR. No model weights are present.
- No live store, service, printer, drawer, NFC, or signature-device behavior was
  verified. No running application was installed, restarted, or upgraded.
- macOS and SmartStudio were not rebuilt or published by this release.

## Exact artifacts

- Update: `smartstock-windows-1.1.72.zip`, 167455159 bytes.
  SHA-256: `d2644e4b0c5289476c813ed50e62abca937c3a9e83552e744ddee3a549fb8121`.
- Installer: `smartstock-windows-setup-1.1.72.exe`, 580515186 bytes.
  SHA-256: `59f9d9d3ca21b1d6699c7177f30112f30e317c669b6dbcac0285e6fbac9a2ee0`.
- Standalone model-migration bridge and its SHA-256 are retained beside the
  artifacts. AI model files remain in persistent store profiles.

## Publication

The complete hosted update ZIP was downloaded and verified before app-release
metadata publication. Independent metadata readback matched the exact version,
build, R2 path, size and hash. The installer was fully downloaded and verified
before the public Windows selection was changed. An independent public download
from https://downloads.deckers.gy/download/windows matched its exact size and
SHA-256. Both Windows update and installer publications completed successfully.

Update the store server first, then registers. The historical import runs on the
server's authoritative local database when the upgraded server starts. Publishing
does not run the import on the current register or upgrade POS-SERVER.

Evidence is retained under `SmartStock/target/release-windows-1.1.72`. Never
rebuild or replace artifact bytes under a published version.
