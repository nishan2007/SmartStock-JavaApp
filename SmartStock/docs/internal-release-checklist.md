# SmartStock Internal Release Checklist

Use this checklist before calling an internal store build ready.

## Build
- Run `mvn -q test`.
- Run `mvn -q package`.
- Confirm `target/inventory-management-1.0.0.jar` exists.
- Confirm `target/dependency/` contains runtime dependencies.

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
- Run the macOS installer on a fresh workstation profile.
- Confirm database setup, credential loading, app launch, and sync service installation.
- Confirm backup scheduler settings persist after app restart.
- Publish update metadata only after backup/restore and installer checks pass.

## Release Notes
- Keep public signing/notarization out of scope for internal-only release candidates.
- Do not bump `pom.xml` from `1.0.0` until the release candidate is accepted.
