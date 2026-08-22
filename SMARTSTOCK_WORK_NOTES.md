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
- Windows 1.0.80 packaging passed. Exact artifacts:
  - `smartstock-windows-1.0.80.zip`: 34,149,274 bytes, SHA-256 `07CC2E02A8A80B2B532E405D09BBEF2CE6BF0FD125E0078987CA358C8C6973DF`
  - `smartstock-windows-setup-1.0.80.exe`: 52,395,900 bytes, SHA-256 `3EF8CEDEB19886127EC92AD1088B51C949A7810AF44D8A120A7D1C70AE037BCC`
- The ZIP contents and application JAR manifest were inspected; the manifest reports SmartStock 1.0.80 and the new cache/pool/printing classes are present.
- Silent installation of the rebuilt installer could not complete because the admin-required installer waited for an interactive Windows UAC approval. The waiting installer was stopped, so the currently installed copy is not claimed as verification of this exact artifact.
- No live PostgreSQL server, service, physical printer, drawer, or NFC verification was performed.

## Remaining work

1. Install the rebuilt SmartStock 1.0.80 installer interactively with administrator approval and verify the installed application.
2. Restart the server service so the connection pool and credential cache become active.
3. Configure release credentials/tooling, then upload, download, and verify the exact remote artifact before publishing release metadata.
4. Perform the live performance and hardware checks below.
5. Remeasure:
   - `/v1/health` first and warm response times.
   - Login and main-menu load time.
   - Inventory, sales, customer, reports, and company-preferences load times.
   - Refresh-button response and visible feedback.
6. Confirm PostgreSQL connection counts remain bounded during parallel screen loads.
7. Verify audit records, device last-seen, and employee-session expiry still update correctly.
8. Perform physical printer, cash drawer, and NFC checks separately if those workflows are included in the release.

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
