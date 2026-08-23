# SmartStock Shared Work Notes

Last updated: 2026-08-21
Branch: `codex/v1-schema-baseline`
Workspace status: Uncommitted changes are present.

## Purpose

This file is the handoff log for SmartStock work performed across computers and Codex chats. Read it before starting work, update it after meaningful changes, and commit/push the branch before moving to another computer.

Do not add credentials, tokens, pairing codes, private keys, database dumps, or production recovery evidence to this file.

## Completed in the latest session

### Document logo printing

- Fixed 40-column custom-order slips so their ESC/POS print job includes the configured company logo as centered monochrome raster data.
- Confirmed the letter custom-order slip renderer paints the supplied logo and added a graphical regression test.
- Fixed quotation, invoice, and delivery-bill logo handling for LAN-managed `smartstock-asset:` references.
- Updated document printing to load public, authenticated, and LAN-managed images through `ImageCacheManager` instead of `new URL(...)`.
- Added regression coverage for custom-order ESC/POS output, letter rendering, and all three quotation/invoice document types.
- Full Maven tests, focused document tests, the repository security check, and `git diff --check` passed.
- Physical 40-column and letter printer verification remains outstanding.

### Sales receipt reprinting

- Added **Reprint Receipt** to View Sales.
- The selected local sale is loaded through the authenticated LAN receipt endpoint and opened in the existing printer/format preview.
- Reprints do not trigger the cash drawer.
- Cross-store cached sales explain that the receipt must be reprinted at its originating store because the synchronized history cache is not a complete receipt payload.
- The focused View Sales regression test and `git diff --check` passed.
- A concurrent/inconsistent Maven output directory caused the later full-suite attempt to report widespread unrelated `NoClassDefFoundError` failures; the focused test passed again afterward.

### Custom-order slip and label printing

- Closing the order-label quantity popup now skips labels without canceling the custom-order slip print.
- When no Order Label Default is configured, order labels now fall back to the configured receipt printer.
- Receipt-printer fallback labels are sent as ESC/POS raster images, with a paper cut after every individual label.
- The custom-order slip already sends its own cut, so receipt-printer fallback order is: slip, cut, label, cut, then each additional label followed by its own cut.
- A configured-but-unavailable label printer still reports an error instead of silently switching printers; fallback applies only when no label printer is configured.
- Added regression tests for popup cancellation and per-label raster/cut output.
- Files:
  - `SmartStock/src/Receipt/CustomOrderLabelPrinter.java`
  - `SmartStock/src/ui/screens/CustomOrderSlipPreview.java`
  - `SmartStock/src/ui/screens/customorders/CustomOrders.java`
  - `SmartStock/test/Receipt/CustomOrderLabelPrinterTest.java`
  - `SmartStock/test/ui/screens/CustomOrderLabelWorkflowTest.java`
- Physical verification with the actual receipt printer remains outstanding.
### Receipt footer

- Added a centered `Powered by SmartStock` line at the bottom of sales receipts.
- File: `SmartStock/src/Receipt/ReceiptFormatter.java`
- Added logo output for the 40-column custom-order slip and for quotation/invoice documents when a configured logo asset is available.

### Register performance investigation

Confirmed findings:

- The register hardware was healthy: low CPU use and sufficient free memory.
- LAN latency to `POS-SERVER` was normally below 1 ms with no packet loss.
- The SmartStock HTTPS health endpoint took approximately 1.76-1.90 seconds on a reused connection and roughly 3.1-4.3 seconds on a new connection.
- The main bottleneck was server-side database access:
  - Every database operation opened a new PostgreSQL connection.
  - Every connection reloaded two Windows DPAPI credentials.
  - Each DPAPI read launched PowerShell.
  - A normal authenticated request could open separate connections for device authentication, employee-session authentication, the operation, and request auditing.
- Health refreshes performed database/schema validation every time.
- Device and employee-session heartbeat rows were updated on every request.
- General screen refreshes can rebuild the screen and silently ignore refresh attempts while a transition is already running.

### Implemented performance improvements

- Added a bounded reusable PostgreSQL connection pool.
  - Default maximum: 16 physical connections.
  - Default acquisition timeout: 10 seconds.
  - Connections are rolled back/reset before being returned to the pool.
  - Invalid connections are discarded and recreated.
  - File: `SmartStock/src/data/PostgresConnectionPool.java`
- Updated `DB.getConnection()` to borrow pooled logical connections.
  - Pool resets when database configuration changes.
  - Pool closes during server shutdown.
  - Failed schema validation returns/closes the borrowed connection.
  - File: `SmartStock/src/data/DB.java`
- Cached decrypted secure credentials in memory.
  - Cache updates after SmartStock writes a credential.
  - Cache invalidates after credential deletion.
  - This removes repeated PowerShell launches during normal database access.
  - File: `SmartStock/src/utils/SecureCredentialStore.java`
- Reset the database pool after database settings are saved.
  - File: `SmartStock/src/data/DatabaseConfig.java`
- Cached local schema-health readiness for 30 seconds.
- Throttled device and employee-session heartbeat writes to avoid rewriting rows on every read request.
- Closed the database pool when the LAN API server shuts down.
  - File: `SmartStock/src/services/LanApiServer.java`
- Added a regression test proving logical connection close returns the connection to the pool and reuses the same physical connection.
  - File: `SmartStock/test/data/PostgresConnectionPoolTest.java`

## Validation completed

- Full Maven test suite: 357 tests run, 0 failures, 0 errors, 10 skipped.
- Focused pool/configuration tests passed.
- `git diff --check` passed.
- Repository security and LAN API cutover checks passed before the final one-line label fallback correction; the final clean Maven suite and `git diff --check` passed after it.
- A 1.0.80 candidate was packaged and installation-tested successfully, but it was superseded before publication when the intended release version was confirmed as 1.0.81. The 1.0.80 artifacts must not be published.
- Windows 1.0.81 packaging and installation verification passed. Exact artifacts:
  - `smartstock-windows-1.0.81.zip`: 34,149,271 bytes, SHA-256 `18A125526397E1C57B465ED0E2AFA322AA29F85ABF91E5B5FD1EBA864AC01581`
  - `smartstock-windows-setup-1.0.81.exe`: 52,396,264 bytes, SHA-256 `1CA61A5498A7D023C87C207D8897DA727C3780178E2975BD7ED712CB71B36E76`
- The 1.0.81 ZIP contents and application JAR manifest were inspected; the manifest reports SmartStock 1.0.81 and the expected new classes are present.
- Interactive installation completed successfully. Both installed launchers report 1.0.81, and the installed JAR exactly matches the rebuilt JAR: SHA-256 `9C9086944954D45AA7B8DE8CDBE03D0413AB5B437BDAFCEC93C8B111E9EEA913`.
- Published SmartStock 1.0.81 build 10081 for Windows to the development update channel.
  - R2 object: `windows/1.0.81/smartstock-windows-1.0.81.zip`
  - The uploaded object was downloaded and matched the expected 34,149,271-byte size and SHA-256 before publication and again after publication.
  - Supabase `app_releases` readback returned exactly one published row matching the object path, size, and SHA-256.
- No live PostgreSQL server, service, physical printer, drawer, or NFC verification was performed.

## 2026-08-22 release 1.0.82

- Fixed custom-order action button text that could render white on white under the Windows look and feel.
- Customer phone changes made during custom-order checkout are now sent to the server, saved to the customer account, and included on the printed order slip.
- Added receipt/lookup barcodes to 40-column and letter custom-order slips and to quotation, invoice, and delivery documents.
- Fixed quotation/invoice/delivery preview logo resolution for secured local assets and increased the document logo size.
- Fixed the main-menu greeting and subtitle contrast on the always-dark ribbon in light mode.
- Clean validation passed: 362 tests, 0 failures, 0 errors, 10 skipped; repository security and LAN API cutover checks passed; `git diff --check` passed.
- Windows 1.0.82 packaging passed. Exact artifacts:
  - `smartstock-windows-1.0.82.zip`: 34,152,451 bytes, SHA-256 `211486E8DB1968F452F223E4CF09934A56A38F1C37A00D023A4B9EC7AC5A1B58`
  - `smartstock-windows-setup-1.0.82.exe`: 52,395,639 bytes, SHA-256 `048B5EA8B616C020384FFCE071F27D9AB9A84BEDB6BEEC7FA620B1AC22D0C847`
- The 1.0.82 ZIP and application JAR manifest were inspected; the manifest reports SmartStock 1.0.82 and the expected new classes are present.
- The standalone installer was not installed because its Windows elevation prompt was canceled. The owner explicitly chose to verify installation through the in-app updater instead; that live updater check remains outstanding until a workstation accepts the published update.
- Published SmartStock 1.0.82 build 10082 for Windows to the development update channel (Supabase release ID 86).
  - R2 object: `windows/1.0.82/smartstock-windows-1.0.82.zip`
  - Pre-publication and post-publication downloads both matched the expected 34,152,451-byte size and SHA-256.
  - Supabase `app_releases` readback returned exactly one published row matching version, build, platform, bucket, object path, size, and SHA-256.
- No live PostgreSQL server, service, physical printer, drawer, or NFC verification was performed.

## 2026-08-22 updater recovery release 1.0.83

- Diagnosed the failed 1.0.82 in-app update from `~/.smartstock/updates/updater.log`: the background sync-service copy still held `xchart-3.8.8.jar`, causing the updater to roll back and exit before relaunching SmartStock.
- The updater now stages and swaps the sync-service app directory as a complete unit. A locked live service directory is left intact and its update is deferred instead of aborting the desktop update.
- Any updater failure now explicitly relaunches the installed SmartStock application after rollback.
- Validation passed before the version-only bump: 363 tests, 0 failures, 0 errors, 10 skipped; repository security, LAN API cutover, and diff checks passed.
- Windows 1.0.83 packaging passed. Exact artifacts:
  - `smartstock-windows-1.0.83.zip`: 34,152,830 bytes, SHA-256 `67481566409868FB5EAAE6A8A88C1BC6B0004316B09B47A5C08DA8EE02372079`
  - `smartstock-windows-setup-1.0.83.exe`: 52,398,125 bytes, SHA-256 `41C575B80C1CAE39549C419E85B4403070C026DB0A951AFF4563AD704EA4FECF`
- Published SmartStock 1.0.83 build 10083 to the development update channel (Supabase release ID 87).
  - R2 object: `windows/1.0.83/smartstock-windows-1.0.83.zip`
  - Pre-publication and post-publication downloads matched the expected size and SHA-256.
  - Supabase readback returned exactly one matching published release row.

## 2026-08-22 drawer reconciliation release 1.0.84

- Reconciled the reported $21,970 Drawer Match Checks total against live local PostgreSQL. All eight sales already referenced valid historical Draw 1 sessions and occurred before those sessions closed; no live transaction or session row was changed.
- Fixed Balance Sheet drawer matching to include intervening sessions after the matched range expands, and added receipt/order, timestamp, device, drawer, and explicit unassigned/unselected-session details.
- Added an administrator-only, audited, idempotent recovery path that can attach only NULL-session legacy cash rows to the compatible current open drawer; it never reassigns a non-NULL session and never closes the session automatically.
- Verified current POS sales, order payments/refunds, invoice payments/refunds, and customer-account cash paths require and preserve an active drawer session.
- Maven tests, repository security checks, Windows packaging, updater ZIP layout validation, and `git diff --check` passed.
- Windows 1.0.84 artifacts:
  - `smartstock-windows-1.0.84.zip`: 34,164,998 bytes, SHA-256 `030FC6F3E6616F26FA131E4A9F111435B885CF0A45E28E627D9947FD323F1B54`
  - `smartstock-windows-setup-1.0.84.exe`: 52,406,587 bytes, SHA-256 `34CC4BBC3D52268CB8A81A80DF688459B8A22CC13F40780259BE1678C9E5676D`
- Published SmartStock 1.0.84 build 10084 to the development update channel (Supabase release ID 88).
  - R2 object: `windows/1.0.84/smartstock-windows-1.0.84.zip`
  - The post-upload R2 download matched the expected byte size and SHA-256.
  - Supabase readback returned exactly one matching published release row.

## 2026-08-22 Balance Sheet and quotation-button hotfix 1.0.85

- Fixed the expanded Balance Sheet drawer-match query by qualifying custom-order payment columns after its join, removing the PostgreSQL ambiguous-column failure seen in 1.0.84.
- Verified the corrected `2026-05-24` through `2026-08-22` query read-only against live local PostgreSQL; no database row was changed.
- Fixed the blank `New Quotation` button under Windows look and feel by preserving an explicitly painted, contrast-checked accent-button palette through theme refreshes.
- Focused regressions, the full Maven suite, repository security checks, Windows packaging, and `git diff --check` passed.
- Windows 1.0.85 artifacts:
  - `smartstock-windows-1.0.85.zip`: 34,165,190 bytes, SHA-256 `76B6AFB5506B1219436CF0E5E39C87E4935CE5B48834FC09EA33D69BE626587A`
  - `smartstock-windows-setup-1.0.85.exe`: 52,406,568 bytes, SHA-256 `512C757569432C73EAF88A495D53EF56A7DE0D6DBF4595AC71FE203B14C14EEB`
- Published SmartStock 1.0.85 build 10085 to the development update channel (Supabase release ID 89).
  - R2 object: `windows/1.0.85/smartstock-windows-1.0.85.zip`
  - The post-upload R2 download matched the expected byte size and SHA-256.
  - Supabase readback returned exactly one matching published release row.

## 2026-08-23 Balance Sheet accounting release 1.0.86

- Added custom-order unpaid balances to `Orders / Charge Account` and Accounts Receivable.
- Later custom-order payments now reclassify the matching charge amount while continuing to display the received payment under cash, card, MMG, or cheque, preventing duplicate income.
- POS sale, custom-order, invoice, and cross-store refund payouts now appear under Expenses instead of being netted into Income.
- Cash refunds remain tied to their drawer session and reduce expected drawer cash; unpaid-balance reductions affect receivables without being reported as payouts.
- Made the R2 publisher portable across macOS and Git Bash by supporting either `shasum` or `sha256sum`, BSD or GNU `stat`, and the repository-local Wrangler executable when `npx` is unavailable.
- Full Maven tests, repository security checks, Windows packaging, updater ZIP layout validation, and `git diff --check` passed.
- Windows 1.0.86 artifacts:
  - `smartstock-windows-1.0.86.zip`: 34,167,144 bytes, SHA-256 `874176CE7CE74D9FD19F721FF55A7E8AC793634A377C2C5E7501734D57A88D9E`
  - `smartstock-windows-setup-1.0.86.exe`: 52,407,536 bytes, SHA-256 `0F021302BE93A9FBBF2473AC18A2B129195CE4BFF15E3F1679B3F5B400ECF99F`
- Published SmartStock 1.0.86 build 10086 to the development update channel (Supabase release ID 90).
  - R2 object: `windows/1.0.86/smartstock-windows-1.0.86.zip`
  - The post-upload R2 download matched the expected byte size and SHA-256.
  - Supabase readback returned exactly one matching published release row.

## Remaining work

1. Restart the server service so the connection pool and credential cache become active.
2. Perform the live performance and hardware checks below.
3. Remeasure:
   - `/v1/health` first and warm response times.
   - Login and main-menu load time.
   - Inventory, sales, customer, reports, and company-preferences load times.
   - Refresh-button response and visible feedback.
4. Confirm PostgreSQL connection counts remain bounded during parallel screen loads.
5. Verify audit records, device last-seen, and employee-session expiry still update correctly.
6. Perform physical printer, cash drawer, and NFC checks separately if those workflows are included in the release.

## Other changes currently present in the workspace

The following inventory/menu files are part of this release and were reviewed together with the LAN performance work:

- `SmartStock/src/services/InventoryCatalogCache.java` (new)
- `SmartStock/src/ui/components/AppMenuBar.java`
- `SmartStock/src/ui/screens/EditItem.java`
- `SmartStock/src/ui/screens/EnterInventory.java`
- `SmartStock/src/ui/screens/MainMenu.java`
- `SmartStock/src/ui/screens/MakeASale.java`
- `SmartStock/src/ui/screens/NewItem.java`

These changes add background inventory warming, shared cached catalog reads, mutation-safe refreshes, and explicit inventory-refresh feedback.

## Cross-computer handoff procedure

Before switching computers:

1. Update this file with completed work, validation, and remaining steps.
2. Review `git status` and `git diff`.
3. Commit intentionally on the current branch.
4. Push the branch.
5. On the other computer, pull the same branch before editing.

Do not copy or synchronize the live working tree through OneDrive, Dropbox, iCloud, or another file-sync service.
