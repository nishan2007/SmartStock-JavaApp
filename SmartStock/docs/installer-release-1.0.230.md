# SmartStock Windows release 1.0.230

Prepared on 2026-10-01. Windows x64, build 100230.

## Changes

Custom orders support customer-supplied items with item type, color, size, and brand. Item type is required; the other fields are optional. These lines require add-ons, have no base charge, and do not deduct stock. Update the store server and registers together.

## Artifacts

- Update: `target/release-windows-1.0.230/smartstock-windows-1.0.230.zip`
- Update size: 296,608,182 bytes
- Update SHA-256: `f4877a49e60eb35cf98c9c631275d5bbdf2f2baed7bae44f665776dfa52ec430`
- Installer: `target/release-windows-1.0.230/smartstock-windows-setup-1.0.230.exe`
- Installer size: 708,740,556 bytes
- Installer SHA-256: `22b595fcf934b163ff6f74c9395551f93277edc01b24f1e26424a7e9957adbec`

## Validation

- Existing Windows packaging script completed Maven package and tests: 760 tests, zero failures/errors, 32 environment-dependent skips.
- Repository security check and `git diff --check` passed.
- All 17 installer portal/publication tests passed; Bash publisher syntax passed.
- Isolated per-user installation completed with exit code 0. Installed launchers, release JAR, Java runtime, PostgreSQL bootstrap installer, photo model, and tunnel executable were present.
- Installed JAR hash matched the packaged JAR. Bundled Java reported version 17.0.20.
- A headless probe using the installed JAR, dependencies, and runtime verified customer-item descriptions and both Swing button callbacks.
- Verification installation was uninstalled successfully; its per-user uninstall registration was removed.

## Publication

The update ZIP was uploaded to R2, downloaded, and verified against its local size and SHA-256 before publishing Windows app-release metadata for build 100230. A subsequent read of the production feed confirmed that 1.0.230 is its latest Windows release, with the expected build number, size, and hash.

The full installer was uploaded in parts, downloaded, and verified against its local size and SHA-256 before switching the private Windows latest selection to 1.0.230. Publication completed successfully at `https://downloads.deckers.gy/download/windows`.

## Remaining live acceptance

Interactive popup and slip-preview review, production database save/reload and server/service upgrade, backup/restore, printer, drawer, and NFC checks were not performed. No Mac package was built. The installed-code probe does not verify a visible workflow or a live order transaction.
