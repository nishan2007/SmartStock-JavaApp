# SmartStock Internal Release Checklist

Use this checklist before calling an internal store build ready.

## Build
- Run `mvn -q test`.
- Run `mvn -q package`.
- Confirm the JAR in `target/` matches the version in `pom.xml`.
- Confirm `target/dependency/` contains runtime dependencies.
- Run the repository security check and `git diff --check`.
- Use the same Maven version on Windows and macOS; metadata build numbers use
  `major * 100000 + minor * 1000 + patch` (`1.1.1` = `101001`).

## Data Safety
- Export a manual `.ssbackup` from Company Preferences.
- Restore that `.ssbackup` into a clean SmartStock database before using it for recovery.
- Confirm restored data includes stores, users, inventory, company preferences, cash drawer data, quotes/invoices, custom orders, product images, employee photos, and employee documents.
- Enable scheduled backups, choose a backup folder, run `Run Backup Now`, then confirm old `.ssbackup` files beyond the configured keep count are deleted.

## Runtime Smoke Test
- Launch the packaged JAR, not only IntelliJ.
- Confirm welcome screen, login, and stay-signed-in restore.
- Confirm sync runs for several cycles without repeated stack traces.
- Complete one sale, one return, one custom order, one quote-to-invoice flow, one account payment, and one balance drawer close.
- Preview or print receipt, quote, invoice, delivery bill, and custom order slip.

## Installer/Update
- On Windows, run `tools/package-windows-release.ps1` and verify the resulting EXE in an isolated installation, including the bundled Java runtime and installed application files.
- To build and publish both the Windows update ZIP and installer, use `tools/package-windows-release.ps1 -Publish` with the protected production publishing credentials configured.
- The update and installer must each be downloaded from storage and verified for byte size and SHA-256 before their respective release metadata or latest selection changes.
- Confirm the public installer download selects the verified installer and that
  operator upload/publication still requires the protected publishing key.
- See `../cloudflare/smartstock-installer-portal/README.md` for portal deployment, Mac installer publication and retrying a failed installer publication without duplicating an app-release row.
- Run the macOS installer on a fresh workstation profile.
- Confirm database setup, credential loading, app launch, and sync service installation.
- Confirm backup scheduler settings persist after app restart.
- Publish update metadata only after backup/restore and installer checks pass.
- Follow [packaging and publishing](release-packaging-publishing.md); build-only
  preparation must not run a publisher or mutate live store installations.

## Independent AI Models
- Verify update ZIPs and installer app images contain no `.onnx` files, and retain
  the ONNX runtime, model notice and BiRefNet licence.
- Run `node tools/verify-app-update.mjs <update.zip> <version>` against the exact
  update ZIP, then run the model publisher and portal tests.
- Stage model weights outside application packaging input. Publish only changed
  model objects; do not upload unchanged weights during an app release.
- Before the first model-free release, update the protected installer portal to
  support multipart model uploads, publish/download-verify both initial models,
  and verify the live catalogue and immutable snapshot.
- Verify the server's signing configuration through Status > AI Models and test
  Fast/Best downloads as ADMIN; ensure non-admin requests are rejected.
- Confirm desktop and service share the server profile model directory and that
  update/rollback does not replace or remove persistent models.
- Run the migration helper before an older Mac's first model-free upgrade.
- Follow [AI model rollout](ai-model-updates.md) and record any outstanding
  installed-app, service, database, printer, drawer or NFC checks.

## Release Notes
- Keep public signing/notarization out of scope for internal-only release candidates.
- Use the release version requested for the candidate and add matching release
  notes. Never reuse an already published version/build for different bytes.
