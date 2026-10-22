# SmartStock 1.1.24 Windows

Build 101024 adds the Publishing Workspace, accessible from Operations,
custom-order media and price-tag printing. It includes editable multipage
publications, masters, templates, merge data, shared LAN projects, portable
archives, exact-size sheet placement, duplex/booklet settings and shop-printing
PDF/image output. Publisher import requires an optional local LibreOffice
converter and reports unsupported content. Existing approved order proofs retain
their original saved format. See [workspace usage](publishing-workspace.md).

The A4 flyer preset uses zero layout margins so its full-page design fits its
sheet; actual printing still checks the printer's imageable area. A regression
test verifies every preset fits its default sheet. This release also includes
the pending image-processing session cleanup correction.

## Validation

- Full Maven packaging suite: 976 tests, zero failures/errors, 57 skipped.
- SmartStudio 0.1.10: all 30 Maven tests pass against the 1.1.24 client.
- Release/model/installer tooling: all 26 tests pass.
- Repository Bash security check and `git diff --check` pass.
- Model-free ZIP verification and hosted immutable model catalogue verification
  pass. No model weights changed or were bundled.
- Exact installer extraction passes; all 24 updater payloads match by SHA-256.
  The bundled PostgreSQL installer matches its pinned SHA-256.
- The extracted native launcher reaches the welcome screen with bundled Java
  17.0.20 and an isolated user home. The verification process was stopped.
- With the exact extracted app JAR and runtime, label/card/flyer/booklet project
  round trips, sheet renders, PDF dimensions/page counts and publishing editor
  startup pass. The editor and representative output were visually inspected.

These checks preserve the current installed store application and services.
Actual setup/service installation, live LAN collaboration and synchronization,
update/rollback, real Publisher conversion, physical printer margins/dimensions,
duplex alignment, barcode scanning, drawer/NFC and macOS verification remain
outstanding.

## Exact artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 149196387 | e39ef075cf378ea4e8a7fc2c1d0c1da4788e0f83410bc02ebb48f506cd70df50 |
| All-in-one installer | 562566850 | fece24e705247a7a9f69885d4a8674aaaa1836ecb90e748b9ef3fd866241b319 |
| Model migration bridge | 3318 | 98fde771303ac0f7e67910000ae094782e0d5226d194ad782444b1d17201c791 |

Publication evidence is saved in the ignored `target/release-windows-1.1.24`
directory. Update the store server before registers. macOS artifacts require a
separate Mac build and validation.

## Publication

The update ZIP and all-in-one installer are published. Their complete hosted
downloads matched the exact local sizes and SHA-256 hashes before release
metadata or installer selection changed. Update metadata readback matches
version, build, platform, object path, bucket, size, hash and published status.
Public installer HEAD returns HTTP 200, the 1.1.24 filename and 562566850 bytes.

Download: https://downloads.deckers.gy/download/windows
