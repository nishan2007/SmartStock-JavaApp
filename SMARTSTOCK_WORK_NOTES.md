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

## 2026-08-23 mobile item and Balance Sheet workflow release 1.0.87

- Added `Save & New` to the mobile Add Item web app. A successful save opens a clean form of the same item type, while edits continue to offer only the normal Save action.
- Added visible red asterisks for the web form's actual required fields, including the required inventory-product photo.
- Fixed Balance Sheet submission success to start the next non-overlapping draft instead of reopening the submitted read-only sheet.
- The new draft clears the submitted draft's matched drawer sessions and automatically carries the submitted CF into Balance BF.
- Verified live submission 5 saved for `2026-05-24` through `2026-08-23` with CF `$51,542.50`; the corrected next draft starts `2026-08-24` with that amount as BF. No live database row was changed during verification.
- JavaScript syntax validation, full Maven tests, repository security checks, Windows packaging, embedded mobile-resource validation, and `git diff --check` passed.
- Windows 1.0.87 artifacts:
  - `smartstock-windows-1.0.87.zip`: 34,167,732 bytes, SHA-256 `A007DBF9E12C6E2757EE26EA6B824CAC0073651A2FA4ED16E7270D62C0AC6FC2`
  - `smartstock-windows-setup-1.0.87.exe`: 52,409,788 bytes, SHA-256 `88B704CFE83AD6AD31BDE26894B0EEC5E0A2F964A65259DF1070AA244B1CD346`
- Published SmartStock 1.0.87 build 10087 to the development update channel (Supabase release ID 91).
  - R2 object: `windows/1.0.87/smartstock-windows-1.0.87.zip`
  - The post-upload R2 download matched the expected byte size and SHA-256.
  - Supabase readback returned exactly one matching published release row.

## 2026-08-23 optional new-item cost price release 1.0.88

- Added a per-location Company Preference to require or allow a blank Cost Price when adding inventory items.
- The preference defaults to required; when disabled, blank cost is stored as `$0.00` in desktop and mobile workflows.
- Added server-side validation, mobile bootstrap exposure, and a guarded local-schema migration.
- Existing products are not rewritten by this preference.
- Full Maven tests, repository security checks, JavaScript syntax validation, Windows packaging, embedded-resource validation, and `git diff --check` passed.
- Windows 1.0.88 artifacts:
  - `smartstock-windows-1.0.88.zip`: 34,169,893 bytes, SHA-256 `96D11A4E0ABBC23E7DBD7F6C49D72AA98AB6E0EE847E1AC79369F9325EBBD7C5`
  - `smartstock-windows-setup-1.0.88.exe`: 52,413,644 bytes, SHA-256 `ADEA9E49F80254EF9DB7AE10C72BE9BE44686D51F190A39C58539220CF9635E3`
- Published SmartStock 1.0.88 build 10088 to the development update channel (Supabase release ID 92).
  - R2 object: `windows/1.0.88/smartstock-windows-1.0.88.zip`
  - The remote download matched the exact local byte size and SHA-256 before metadata publication.

## 2026-08-23 resizable badge template editor release 1.0.89

- Replaced the Badge Template Editor's fixed `760×760` size with a larger screen-aware default up to `1180×900`.
- Made the modal dialog explicitly resizable with a practical screen-constrained minimum size.
- Added horizontal scrolling for the complete alignment/layer toolbar on narrower displays while retaining vertical control scrolling.
- Full Maven tests, repository security checks, JavaScript syntax validation, Windows packaging, packaged-class validation, and `git diff --check` passed.
- Windows 1.0.89 artifacts:
  - `smartstock-windows-1.0.89.zip`: 34,170,213 bytes, SHA-256 `314F66C41389E7508FD7B22BF4360D6366513A152F156E8C147C7BE4DDBA2DD5`
  - `smartstock-windows-setup-1.0.89.exe`: 52,413,858 bytes, SHA-256 `7A372B6CA69266918FC1090896CB06B78D7CEDB69C016DA4FB94852C46558DE0`
- Published SmartStock 1.0.89 build 10089 to the development update channel (Supabase release ID 93).
  - R2 object: `windows/1.0.89/smartstock-windows-1.0.89.zip`
  - The downloaded remote object matched the exact local size and SHA-256 before metadata publication.

## 2026-08-24 native Ethernet printing and location form release 1.0.97

- Added native raw-TCP ESC/POS printing for the commissioned NS8360L at `10.1.1.23:9100` while preserving Windows print queues for fallback, letter-size output, and dedicated label printers.
- Routed receipt, return, account-payment, drawer-close, receipt-label, cutter-test, and cash-drawer-test ESC/POS jobs through the enabled Ethernet transport.
- Made the Company Preferences location-details editor vertically scrollable so all contact, email, Gmail, timezone, and action controls remain accessible on shorter displays.
- Updated the Main Menu ribbon contrast regression for the personalized company greeting field.
- Full Maven tests, targeted layout and printer tests, Windows packaging, updater ZIP layout validation, remote R2 byte-for-byte verification, and `git diff --check` passed. The Bash security script could not start in this Windows session because Git Bash could not create its signal pipe and WSL service startup was denied.
- Windows 1.0.97 artifacts:
  - `smartstock-windows-1.0.97.zip`: 34,182,465 bytes, SHA-256 `19AEA335D6123062A3E9105A25C6E33CAA710A400C6D6D98D4A71E1CF9D62354`
  - `smartstock-windows-setup-1.0.97.exe`: 52,424,352 bytes, SHA-256 `AEE91F0933EFD253A37526AF862DC48238A4BF9DCF7C9A739421F3324BD7B1B0`
- Published SmartStock 1.0.97 build 10097 to the development update channel (Supabase release ID 94).
  - R2 object: `windows/1.0.97/smartstock-windows-1.0.97.zip`
  - Independent Supabase readback returned exactly one published row matching version, build, platform, object path, size, and SHA-256.

## 2026-08-24 Magicard, employee Auth, and quick price-tag release 1.0.98

- Added dedicated Magicard 600 Duo workstation settings, Windows queue selection, CR80 front/back duplex printing, queue health guidance, and a duplex test-card action.
- Replaced the missing employee Auth Edge Function dependency with direct server-side Supabase Auth Admin calls using the workstation's protected server credential.
- Made employee email optional. Blank addresses receive a unique plus alias in the manager's mailbox; supplied employee addresses remain unchanged.
- Added quick temporary price-tag printing through the configured Ethernet or Windows 40-column receipt printer, cutting each tag separately.
- Full Maven tests, repository security checks through Git Bash, Windows packaging, updater ZIP layout validation, and `git diff --check` passed.
- Windows 1.0.98 artifacts:
  - `smartstock-windows-1.0.98.zip`: 34,191,633 bytes, SHA-256 `374DAD3D46154B8C323128B32F9835923DE9094DD002DAE4A79F42DA034B52BC`
  - `smartstock-windows-setup-1.0.98.exe`: 52,437,489 bytes, SHA-256 `B63EAF19E41DD1DCB40EE7B3037D73D6A6826B3D08AE3025BE147292B7A3C3AB`
- Published SmartStock 1.0.98 build 10098 for Windows to both development and production.
  - R2 object: `windows/1.0.98/smartstock-windows-1.0.98.zip`
  - Each Supabase project independently returned exactly one matching published release row.
  - A final independent R2 download matched the exact local byte size and SHA-256 after both publications.
- Live installed-app, Magicard badge, receipt price-tag, cash-drawer, and NFC checks remain separate hardware validation steps.

## 2026-08-24 Ethernet-only receipt-printer hotfix 1.0.99

- Fixed direct Ethernet ESC/POS receipt printing when no Windows receipt-printer queue is installed or selected.
- Test Receipt, Test Cutter, and Test Drawer now attempt the configured Ethernet endpoint before requiring a Windows fallback queue.
- Full Maven tests, repository security checks through Git Bash, Windows packaging, updater ZIP layout validation, and `git diff --check` passed.
- Windows 1.0.99 artifacts:
  - `smartstock-windows-1.0.99.zip`: 34,191,847 bytes, SHA-256 `A5CABC3F986E494705C949529BF8CC7F67A85DAC434936F91A2ECAC2E8F80064`
  - `smartstock-windows-setup-1.0.99.exe`: 52,436,498 bytes, SHA-256 `A780FBC48271258B1055BA208C5895CFA911CC04A5F43A8D197E8A6C2F7990C1`
- Published SmartStock 1.0.99 build 10099 for Windows to both development and production.
  - R2 object: `windows/1.0.99/smartstock-windows-1.0.99.zip`
  - Each Supabase project independently returned exactly one matching published release row.
  - A final independent R2 download matched the local updater ZIP exactly after both publications.
- A physical receipt, cutter, and cash-drawer test remains a separate hardware validation step.

## 2026-08-24 manual breaks and effective-dated payroll release 1.0.100

- Removed automatic employee break ending; employees explicitly start and stop their breaks.
- Added effective-dated compensation type and pay-rate history using the employee's current pay-period boundary.
- Rate changes affect all hours in the current open period while older closed periods retain the previously effective rate.
- Added an automatic local schema migration and backfill that runs during LAN API server startup before schema validation.
- Routed manual clock-out, automatic clock-out, time-clock corrections, payroll reports, and cross-store reference synchronization through the effective rate for each work date.
- Full Maven tests, repository security checks through Git Bash, Windows packaging, updater ZIP layout validation, and `git diff --check` passed.
- Windows 1.0.100 artifacts:
  - `smartstock-windows-1.0.100.zip`: 34,194,803 bytes, SHA-256 `7254980DEAE66F95E1817CD000FEF0260001AC5DDD53E8BB9179BF87F02A9139`
  - `smartstock-windows-setup-1.0.100.exe`: 52,440,150 bytes, SHA-256 `92E06C245E5CDCF39AC27468F55B836EDAC26028A01394341373E22647603A18`
- Published SmartStock 1.0.100 build 10100 for Windows to both development and production.
  - R2 object: `windows/1.0.100/smartstock-windows-1.0.100.zip`
  - Each Supabase project independently returned exactly one matching published release row.
  - A final independent R2 download matched the local updater ZIP exactly.
- Live installed-app and production-database migration verification remains an operational check after updating the store server.

## 2026-08-24 payroll baseline repair release 1.0.101

- Added an idempotent follow-up migration that creates the effective pay-rate baseline for any employee missing one while preserving all existing history.
- Production was repaired and verified with all 6 active employees covered, zero missing baselines, and zero incomplete history rows.
- Full Maven tests, repository security checks through Git Bash, Windows packaging, updater ZIP validation, and `git diff --check` passed.
- Windows 1.0.101 artifacts:
  - `smartstock-windows-1.0.101.zip`: 34,195,564 bytes, SHA-256 `A67615F0A268AE095F5DD5781CD2989C478A1CCDF9BD6F984DA3D17B28642D4D`
  - `smartstock-windows-setup-1.0.101.exe`: 52,439,322 bytes, SHA-256 `B876862CACCA3AD5FD9FC9DEDCD45D28241C5AF07E27649BB1925749014538FA`
- Published SmartStock 1.0.101 build 10101 for Windows to development and production.
  - R2 object: `windows/1.0.101/smartstock-windows-1.0.101.zip`
  - Both release projects returned exactly one matching row, and the final R2 download matched the local ZIP.

## 2026-08-25 badge printing and mobile item printing release 1.0.102

- Fixed missing or rectangular Position and Badge ID characters when employee badges are printed through Bodno/Magicard Windows drivers.
- Physical badge pages are flattened to opaque RGB, with a safe system-font fallback for unsupported configured glyphs.
- Added Mobile Item Web App Save and Print and direct Ethernet ESC/POS fallback improvements included in the 1.0.102 release notes.
- Full Maven tests, Windows packaging, updater ZIP layout validation, R2 byte-for-byte verification, and `git diff --check` passed.
- Windows 1.0.102 artifacts:
  - `smartstock-windows-1.0.102.zip`: 34,198,687 bytes, SHA-256 `123686F5FDD09DAB412C04FEEC8FF21B09D40DA1663156D0B27E5049E29A8B49`
  - `smartstock-windows-setup-1.0.102.exe`: 52,442,639 bytes, SHA-256 `B6FA60E15619E9A7A9CF08637F5101DB322D872566947B8A2B5BB67941328734`
- Published SmartStock 1.0.102 build 10102 for Windows to production.
  - R2 object: `windows/1.0.102/smartstock-windows-1.0.102.zip`
  - Supabase returned exactly one matching published release row, and the downloaded R2 object matched the local updater ZIP.
- A physical Bodno/Magicard badge print remains a separate hardware verification step.

## 2026-08-25 dual NFC-card release 1.0.103

- Added automatic MIFARE Classic 1K support alongside the existing NTAG and MIFARE Ultralight NFC Type 2 path.
- Classic badge records use authenticated sector 1 data blocks 4-6 with CRC integrity validation; trailer block 7 is never written.
- A live read-only ACR122U check authenticated the physical MIFARE Classic 1K card and correctly identified that it did not yet contain a SmartStock record.
- Full Maven tests, repository security checks, Windows packaging, updater ZIP layout validation, R2 byte-for-byte verification, and `git diff --check` passed.
- Windows 1.0.103 artifacts:
  - `smartstock-windows-1.0.103.zip`: 34,201,838 bytes, SHA-256 `E611D469D015452251CB0AAD66EACF6309055CA272ABCCEB6B6B57B6DA5EC6F0`
  - `smartstock-windows-setup-1.0.103.exe`: 52,446,158 bytes, SHA-256 `3BD9836B86A40D70EFF9ACAA61837C73BD349903D6F1DF5C13199D7AE6B0FD9E`
- Published SmartStock 1.0.103 build 10103 for Windows to production.
  - R2 object: `windows/1.0.103/smartstock-windows-1.0.103.zip`
  - Supabase returned exactly one matching published release row, and the downloaded R2 object matched the local updater ZIP.
- Live write/read-back, login, time-clock, and manager-authorization checks with a programmed Classic card remain physical verification steps after updating the workstation.

## 2026-08-26 configurable rounding and inventory price review release 1.0.104

- Added independent Company Preferences switches for nearest-$20 rounding in sales and custom orders.
- Added the View Inventory price-review workflow for finding and selectively correcting catalog prices that are not multiples of $20.
- Full Maven tests, repository security checks, Windows packaging, updater ZIP layout validation, R2 byte-for-byte verification, and `git diff --check` passed.
- Windows 1.0.104 artifacts:
  - `smartstock-windows-1.0.104.zip`: 34,217,288 bytes, SHA-256 `520566A5FEAB91AE432D9F0411A3CD67637F98C761781A379D02AA20516E06CB`
  - `smartstock-windows-setup-1.0.104.exe`: 52,459,895 bytes, SHA-256 `7644D503EFFC1D5EB9DC824B46EABBABC11CD4ED2AFFE2DE62E650310B8A0F05`
- Published SmartStock 1.0.104 build 10104 for Windows to production.
  - R2 object: `windows/1.0.104/smartstock-windows-1.0.104.zip`
  - Supabase returned exactly one published row matching version, build, platform, object path, size, and SHA-256.
- Live installed-app, database migration, printer, cash-drawer, badge, and NFC checks remain separate verification steps.

## 2026-08-26 complete Make a Sale catalog release 1.0.105

- Fixed Make a Sale searches that could miss products outside the first 250 alphabetically cached items.
- The register now loads the complete catalog into its shared Make a Sale cache; non-empty interactive server searches retain a 250-result response cap.
- Full Maven tests, repository security checks, Windows packaging, updater ZIP layout validation, R2 byte-for-byte verification, and `git diff --check` passed.
- Windows 1.0.105 artifacts:
  - `smartstock-windows-1.0.105.zip`: 34,217,462 bytes, SHA-256 `6D9807B1E4B8F391914327E7ECEE1196363890C371F73878BF9214F844EF542E`
  - `smartstock-windows-setup-1.0.105.exe`: 52,459,436 bytes, SHA-256 `74744C6ACC4B29F7165D6A9DDAF7C018E307BFB3EF56287A46F0636609A9E2C9`
- Published SmartStock 1.0.105 build 10105 for Windows to production.
  - R2 object: `windows/1.0.105/smartstock-windows-1.0.105.zip`
  - Supabase independently returned exactly one published row matching version, build, platform, bucket reference, object path, size, and SHA-256.
- Both the store server and registers must update to 1.0.105 for the complete-catalog Make a Sale fix; live installed-app verification remains outstanding.

## 2026-08-26 checkout drawer and Make a Sale display release 1.0.106

- Added a drawer-only ESC/POS operation so cash sales completed with Checkout Only open the configured drawer without printing or cutting paper.
- Added a dedicated Size column to the Make a Sale cart, including resumed held carts, and rebalanced the search dropdown to provide more Description space.
- Made product images optional when creating Service items while retaining the requirement for Inventory and Non-Inventory items.
- Full Maven tests, repository security checks, Windows packaging, updater ZIP layout validation, R2 byte-for-byte verification, and `git diff --check` passed.
- Windows 1.0.106 artifacts:
  - `smartstock-windows-1.0.106.zip`: 34,218,266 bytes, SHA-256 `0102024C5E2EB94388CB6E4280B555FC816A20F865BEFE04CDD0182CFC78D5C4`
  - `smartstock-windows-setup-1.0.106.exe`: 52,460,328 bytes, SHA-256 `C12DE4CF09DBCC125208A29734C185F20419C26E282D3E1925E66A9F4C6E0C93`
- Published SmartStock 1.0.106 build 10106 for Windows to production.
  - R2 object: `windows/1.0.106/smartstock-windows-1.0.106.zip`
  - Supabase independently returned exactly one published row matching version, build, platform, bucket reference, object path, size, and SHA-256.
- Live installed-app and physical cash-drawer behavior remain separate verification steps.

## 2026-08-26 Edit Item service-image correction release 1.0.107

- Updated Edit Item so Service items can be saved without an image, including when an existing item is changed to Service.
- Inventory and Non-Inventory items continue to require images in both New Item and Edit Item.
- Full Maven tests, repository security checks, Windows packaging, updater ZIP layout validation, R2 byte-for-byte verification, and `git diff --check` passed.
- Windows 1.0.107 artifacts:
  - `smartstock-windows-1.0.107.zip`: 34,218,358 bytes, SHA-256 `835ED5398F98A6346EADEE2483FA6BDF6D17810F27DFBDFE70FDD641AA001A7C`
  - `smartstock-windows-setup-1.0.107.exe`: 52,461,474 bytes, SHA-256 `317BC64CDBC2FFF11DB4450BF1741D9A6EF396A48ADD326CC408E24CE6ACFEF9`
- Published SmartStock 1.0.107 build 10107 for Windows to production.
  - R2 object: `windows/1.0.107/smartstock-windows-1.0.107.zip`
  - Supabase independently returned exactly one published row matching version, build, platform, bucket reference, object path, size, and SHA-256.
- Live installed-app verification remains outstanding.

## 2026-08-26 usage-sorted Service Quick Picks release 1.0.108

- Quick Pick Service Items now sort by total quantity sold at the current store, descending, with stable name and product-ID tie-breakers.
- Never-used services follow used services alphabetically, while normal catalog searches remain alphabetic.
- Full Maven tests, repository security checks, Windows packaging, updater ZIP layout validation, R2 byte-for-byte verification, and `git diff --check` passed.
- Windows 1.0.108 artifacts:
  - `smartstock-windows-1.0.108.zip`: 34,218,799 bytes, SHA-256 `7CA307F92E44F7C8BEC6D571E29F1812BA843E03D7016BAD42520A46A22B510A`
  - `smartstock-windows-setup-1.0.108.exe`: 52,460,697 bytes, SHA-256 `0AB6FEBB576F76DF014B136301578E76A2AE01CD705835C46CCB63A5170854B8`
- Published SmartStock 1.0.108 build 10108 for Windows to production.
  - R2 object: `windows/1.0.108/smartstock-windows-1.0.108.zip`
  - Supabase independently returned exactly one published row matching version, build, platform, bucket reference, object path, size, and SHA-256.
- Both the server and registers must update for the usage-sorted Quick Picks; live installed-app verification remains outstanding.

## 2026-08-26 customer payment balance compatibility release 1.0.109

- Fixed open customer-balance loading on servers with a partially upgraded cross-store customer-credit cache schema.
- Added backward-compatible balance calculation and complete partial-upgrade detection/repair.
- Full Maven tests, repository security checks, Windows packaging, updater ZIP layout validation, R2 byte-for-byte verification, and `git diff --check` passed.
- Windows 1.0.109 artifacts:
  - `smartstock-windows-1.0.109.zip`: 34,219,223 bytes, SHA-256 `A27BE7AE455E2775514C78FBF97161DD8E1528387EA00D60A8A22D87AEE8718C`
  - `smartstock-windows-setup-1.0.109.exe`: 52,461,848 bytes, SHA-256 `D167AD49156B24A23A30366EF6F484E41A24423787A3B352BA5B198FDA4C6160`
- Published SmartStock 1.0.109 build 10109 for Windows to production.
  - R2 object: `windows/1.0.109/smartstock-windows-1.0.109.zip`
  - Supabase independently returned exactly one fully matching published row.
- The server must update and restart for the customer-balance fix; live payment verification remains outstanding.

## 2026-08-26 customer open-balances SQL fix release 1.0.110

- Fixed the customer payment window failing to load open balances because the first branch of the UNION did not expose the `document_number` alias requested by the outer query.
- Added `CustomerOpenBalancesSqlArchitectureTest` to guard the required result alias.
- Validation passed: full Maven tests, repository security check, and `git diff --check`.
- Windows 1.0.110 artifacts:
  - `smartstock-windows-1.0.110.zip`: 34,219,280 bytes, SHA-256 `852FBA8D7E61838E882C32A55EAA1C812F7785C5E5552D71634B7AD2914652FB`
  - `smartstock-windows-setup-1.0.110.exe`: 52,460,254 bytes, SHA-256 `EA466ABCA397DAB25A07FE8D7ABC41B8BC651772D7168F31E305496772DDBD29`
- Published SmartStock 1.0.110 build 10110 for Windows to production.
  - R2 object: `windows/1.0.110/smartstock-windows-1.0.110.zip`
  - The publisher downloaded the object and verified its size and SHA-256 before creating release metadata.
- The server and register must update to 1.0.110, and the server service must restart before live payment verification.

## 2026-08-26 Balance C/F customer-payment release 1.0.111

- Updated every live Balance C/F calculation to include customer-account payments while keeping receivable collections separate from new Income.
- Updated saved balance-sheet recalculation so customer payments remain included after an edit.
- Added `BalanceSheetAccountPaymentsCfArchitectureTest`; the screenshot example now calculates `$79,912 + $83,460 = $163,372` before expenses and payables.
- Validation passed: targeted regression test, full Maven tests, repository security check, and `git diff --check`.
- Windows 1.0.111 artifacts:
  - `smartstock-windows-1.0.111.zip`: 34,219,104 bytes, SHA-256 `09616F4555ADC7C033A938242E9FD208841165253A0AC401F166B272B03B4BDC`
  - `smartstock-windows-setup-1.0.111.exe`: 52,460,627 bytes, SHA-256 `8EEEDB7E3A2FEEEE5071AEC568E83E10C35AB7919862FA9F466A17033D41D155`
- Published SmartStock 1.0.111 build 10111 for Windows to production.
  - R2 object: `windows/1.0.111/smartstock-windows-1.0.111.zip`
  - The publisher downloaded and verified the stored ZIP before Supabase returned the matching release record.
- The server must update and restart for the Balance C/F calculation change; live installed-app verification remains outstanding.

## 2026-08-27 miscellaneous sale item release 1.0.112

- Added permission-controlled miscellaneous sale items with cashier-entered names, unit prices, and whole-number quantities.
- Miscellaneous lines are non-inventory, zero-VAT, preserved through held carts, receipts, history, reports, and synchronization, and excluded from returns.
- Existing store databases automatically receive the ordered schema upgrade and protected non-inventory catalog anchor before schema validation.
- Validation passed: full Maven tests, repository security check, clean PostgreSQL 17 migration/schema integration, updater ZIP layout validation, Windows packaging, and `git diff --check`.
- Windows 1.0.112 artifacts:
  - `smartstock-windows-1.0.112.zip`: 34,224,754 bytes, SHA-256 `0763546B4E0D0B5654A52ADFC5DD029A73DC92CF95ABA48C041F2A4D51A16DAB`
  - `smartstock-windows-setup-1.0.112.exe`: 52,467,905 bytes, SHA-256 `D350EC98BC775DBC0395022725EDF43195F19AB4CFDD20B79BFD1B5459D27FFF`
- Published SmartStock 1.0.112 build 10112 for Windows to production.
  - R2 object: `windows/1.0.112/smartstock-windows-1.0.112.zip`
  - The publisher downloaded the stored ZIP and verified its size and SHA-256 before publication; an independent Supabase read returned exactly one matching published row.
- The server and registers must update to 1.0.112. Live installed-app, database, printer, drawer, and NFC verification remains outstanding.

## 2026-08-27 catalog-price normalization release 1.0.113

- Fixed false manager price-override prompts when a fractional catalog price is displayed and charged using SmartStock's canonical whole-currency normalization.
- Direct checkout and held-cart validation now compare the same normalized catalog price; miscellaneous items retain two-decimal precision.
- Validation passed: targeted rounding regression tests, all 431 Maven tests, repository security check, Windows packaging, updater ZIP layout validation, R2 byte-for-byte verification, independent Supabase release-row verification, and `git diff --check`.
- Windows 1.0.113 artifacts:
  - `smartstock-windows-1.0.113.zip`: 34,224,945 bytes, SHA-256 `C6F5A48E2EE23E6B4B8A629E3DC2A63B302DFA4A53C49A75C65D57C0D8E3A9AC`
  - `smartstock-windows-setup-1.0.113.exe`: 52,468,982 bytes, SHA-256 `302EEE0092AC1CB975D7039BEFEAC2D1E52E65357FDEBC3D5FD37441470A3FC4`
- Published SmartStock 1.0.113 build 10113 for Windows to production.
  - R2 object: `windows/1.0.113/smartstock-windows-1.0.113.zip`
  - The publisher downloaded and verified the stored ZIP before publication; Supabase independently returned exactly one matching published row.
- The server and registers must update to 1.0.113 for the rounding fix. Live installed-app checkout verification remains outstanding.

## Remaining work

### 2026-08-31 workflow release 1.0.133

- Packaged Windows 1.0.133 build 10133 after the complete Maven test suite, repository security check, packaging archive checks, and whitespace checks passed.
- Updater ZIP: `SmartStock/target/release-windows/smartstock-windows-1.0.133.zip`, 53,170,444 bytes, SHA-256 `6d8fc158a4e510476ec8d5aad41f2cc510a70ce0af2e037e01d79d597b57d2d3`.
- Local installer: `SmartStock/target/release-windows/smartstock-windows-setup-1.0.133.exe`, 65,827,908 bytes, SHA-256 `36f61cfc5f58a22b11b58fafe8fc45ea06e1fe4c06733701f70e2fbe9035d74d`.
- Production publication is pending authorization for the external R2 artifact upload and Supabase release-catalog write.
- Installed-app upgrade, live database/service, receipt printer, cash drawer, NFC, and real sale timing verification remain outstanding.

### 2026-08-31 checkout timing release 1.0.132

- Packaged Windows 1.0.132 and published build 10132 to the production update catalog after downloading and verifying the uploaded R2 ZIP.
- Updater ZIP: `windows/1.0.132/smartstock-windows-1.0.132.zip`, 53,157,593 bytes, SHA-256 `2c0b1c5baf9c726b40b4bdf29ff3d18188d3d2ba4bd7f20488a3c34549b23236`.
- Local installer: `SmartStock/target/release-windows/smartstock-windows-setup-1.0.132.exe`, 65,816,973 bytes, SHA-256 `82b99fd807b3df035b7ff3f7cea8de376b1333ed00a7425b287eb1ff65f50f52`.
- Full Maven tests, Windows packaging/archive checks, repository security check, and whitespace checks passed. Production release-row readback confirmed publication.
- Persists checkout timing entries in the user's `.smartstock/checkout-timing.log`, with timestamps, a 1 MiB rotation threshold, and one backup. Diagnostic write failures cannot fail checkout.
- No local installation performed, per installation preference. Installed-app upgrade, live database/service, printer, cash drawer, NFC, and real sale timing verification remain outstanding.

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

### Release installation preference (2026-08-31)

- Do not install or update the locally installed SmartStock application unless the user explicitly requests installation. Packaging and publishing do not authorize installation. The user will test deployment through the in-app updater.
- Windows 1.0.129 build 10129 is published to production. Independent release-row readback confirmed the updater ZIP path `windows/1.0.129/smartstock-windows-1.0.129.zip`, size 53,117,033 bytes, SHA-256 `a85c0e1dd7a30540b4d2a039149873f157a900048e1f972c8b4267bd635bb0ba`, and `published=true`.
- Wallet signing configuration, iPhone enrollment, physical scanner, live database upgrade/synchronization, and certified NFC-provider validation remain separate deployment checks. True Wallet NFC remains disabled.

Before switching computers:

1. Update this file with completed work, validation, and remaining steps.
2. Review `git status` and `git diff`.
3. Commit intentionally on the current branch.
4. Push the branch.
5. On the other computer, pull the same branch before editing.

Do not copy or synchronize the live working tree through OneDrive, Dropbox, iCloud, or another file-sync service.
