# SmartStock 1.1.71 / SmartStudio 0.1.63 Windows release

Prepared 21 October and application updates published 22 October 2026.
Versions: SmartStock 1.1.71 build 101071; SmartStudio 0.1.63 build 1063.

The other active repository chats completed before preparation began. Release
notes include the cumulative changes, loyalty, expense/loss ledgers, shared
fonts and latest template work. SmartStudio uses the 1.1.71 client dependency.

## Validation completed

- SmartStock Maven install/tests: 1230 tests, zero failures/errors, 111 skipped.
- SmartStudio Maven package/tests: 228 tests, zero failures/errors.
- Website: 38 tests, TypeScript check and production build passed.
- Storefront gateway: 62 tests passed. No gateway changes since the 1.1.70
  release source snapshot were identified, so no redeployment was performed.
- Repository security check and git diff --check passed.
- 1124 release source inputs recorded and checked unchanged during preparation.
- SmartStock update ZIP passed model-free archive and hosted model catalogue
  readiness verification. No model changes were identified.
- SmartStudio installer extraction, exact JAR identities, feature classes and
  native launcher startup passed. Login preview was visually reviewed.
- Protected release metadata query confirmed SmartStock 1.1.71 was unused.

## Verified artifacts

The initial disk-space blocker was resolved before the final packaging attempt.
Both the exact update ZIP and extracted all-in-one installer passed application
JAR identity, model exclusion and isolated native launcher startup checks.

Update: smartstock-windows-1.1.71.zip, 167452479 bytes.
SHA-256: dedd978ac967a65b2081638e850e103edd212f5d5f35aa9209b482dc250fbccb
The full hosted object was downloaded and verified before metadata creation;
independent metadata readback matched version, build, object path, size and hash.

Installer: smartstock-windows-setup-1.1.71.exe, 580514387 bytes.
SHA-256: 7db4ca200dd4135586c816e4ec941b24f6b14555114e6e61e906fc489d1a08cd
The publisher verified the complete hosted installer before selecting it as the
public Windows download: https://downloads.deckers.gy/download/windows
An independent full public download also matched its exact size and SHA-256.

SmartStudio: see SmartStudio/docs/release-0.1.63.md. Its full public installer
download and metadata readback matched the accepted artifact. Build, security,
test, source snapshot and acceptance evidence remains in both target directories.
Do not rebuild or replace bytes under these published versions.

Live database migration, Windows services, installed upgrade/rollback, loyalty
authority configuration, authenticated workflows, printer/drawer/NFC, shared-font
cross-computer behavior and template visual/physical fidelity remain separate
verification steps. The loyalty chat reported a pre-existing overseas-order SQL
issue blocking full cloud-install rehearsal; its new loyalty cloud migration
passed independently. Update the store server before registers and Studio.
No live installation, database migration or macOS build was performed.
