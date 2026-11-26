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
- Published build 10133 to production after downloading and verifying the uploaded R2 ZIP; Supabase returned the matching release row.
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

### Plaque designer — source implementation (2026-10-29)

- Added active plaque variants and their existing template dimensions/photos to storefront snapshots, with private image metadata redacted and published item/variant image lookup.
- Added a browser plaque designer using shared shirt fonts/layer types, real template-inch controls, per-variant drafts, image upload, JSON save/restore, artwork PNG export, and nine photo-traced procedural 3D shapes.
- Template dimensions are the artwork area. Overall size, approximate depth/material, and preview placement remain separate. Unknown shapes have no substitute 3D model.
- Website build, 73 JavaScript tests, TypeScript, browser acceptance for all nine shapes/mobile/draft isolation/export, relevant Maven storefront tests, security check and scoped whitespace check passed. Maven native staging was skipped for the test run after an existing DirectML cache file lock; no installed-app validation was performed.
- Server/frontend deployment is still pending the normal release/install workflow. No release was published and the installed store server was not updated. See SmartStock/docs/website-plaque-designer.md.

### Plaque preview refinement (2026-10-29)

- Replaced parent-item entry cards with individual active variant photo cards; each opens the correct editor. Model/reference-photo clicks return to artwork editing, while drags rotate the preview.
- Added detailed photo-space silhouettes and photo-projected front textures for the nine plaques, preserving their actual faceting, curved bodies, inserts, star ornamentation and bases. Unmeasured side/rear geometry and artwork placement remain approximate.
- Fixed front-surface artwork visibility and a canvas lifecycle issue. Browser checks now require the photo-textured canvas to remain mounted, verify changed artwork changes the rendered image, and exercise all nine models plus drafts/downloads/mobile layout. All 73 JavaScript tests and the normal website build pass. Source changes remain local; production deployment is still pending.

### Plaque designer deployment (2026-10-29)

- Prepared the normal model-free Windows 1.1.96 updater package and verified its layout, size and SHA-256, including the transferred copy. The full Maven test run completed with no failures/errors; database/hardware-dependent tests retain their documented skips. Isolated updater installation retained its rollback copy and model sentinel.
- Installed 1.1.96 on POS-SERVER using the standard updater. An orphaned old server process initially held the service copy open; recovery retained 1.1.95, then a controlled stop and retry completed successfully. Pinned HTTPS health, installed version, schema readiness, and LAN discovery passed. Server and dedicated tunnel tasks are running. All three profile model files retain valid SHA-256 content hashes.
- Deployed the website bundle extracted from the exact installed updater artifact while preserving the live gateway, origin configuration, secrets, and browse-only restrictions. No global app-release metadata or installer selection was published.
- Verified all nine real plaque models through the public website, all active variant photos, exclusion of the inactive diamond, advancing catalog synchronization, and live card/model click-to-edit behavior. Public catalogs contain no private image manifests. Physical print alignment remains a separate staff verification step.

### Custom item Item Type inheritance (2026-10-29)

- Exposed the main-item Item Type prominently in Windows and shared web add/edit forms, with read-only inherited values in variant forms. Fixed classification lookup loading when selector inputs are embedded separately.
- Added Item Type to Custom Inventory: main/group rows edit it, variants inherit it without overrides or duplicate stored values. Main-row draft edits update expanded children and type searches.
- Added parent-derived item type to the web inventory API, cards, and item details. Reused the existing item-type schema and authenticated catalog save path; no migration is needed.
- Targeted Swing/service tests passed, including all four InventoryWebDatabaseTest cases on a disposable localhost PostgreSQL instance; that test database was stopped afterward. Web unit/browser checks passed for both Add Item and Edit Item and inherited inventory details. Security and scoped whitespace checks passed. This feature is not deployed to the installed app or hosted web assets yet.

## 2026-10-29 combined Windows update 1.1.97 published
- Shared Maven version 1.1.97, build 101097. Standard UpdateOnly package includes the completed SmartStock working-tree changes across chats; SmartStudio remains separate. Custom-photo generation/review work still active in its chat was not claimed complete.
- Artifact: smartstock-windows-1.1.97.zip; 168671721 bytes; SHA-256 78e5a3dc95d93bb2a31254e4281168b14cd5b1ea44cff68301d51666bbeaade3.
- Published via standard protected Windows/R2 publisher. Complete remote download size/SHA matched before app_releases insertion; independent read-back confirmed version/build/platform/path/hash/size/published. Existing first-install selection unchanged.
- Validation: Maven package/full suite 1417 tests, 0 failures/errors, 152 skipped; security check and git diff --check passed; website build and 73 tests passed; six custom-item web tests plus Edge browser add/edit/variant/inventory checks passed. Exact ZIP verified model-free; isolated standard updater installed matching JAR, retained previous-version backup and preserved profile model sentinel.
- No live installations or service restarts performed for this release. POS-SERVER must update before registers. Live database, installed Windows service and physical printer/drawer/NFC checks remain separate from this isolated acceptance.

## 2026-10-29 custom-item AI photo review preparation

- Retrieved current custom-item metadata and original photos through the authenticated store LAN API. Excluded shirts and plaques; retained operational data outside the repository.
- Generated 30 custom-item main-photo previews and 12 variant previews for items with variant-only source photos. All 42 remain unapproved; no live catalog photos were changed.
- Prepared source support for the existing Product Photo Review workflow to resolve custom owners using the existing POS signed-ID convention. Promotion retains original gallery photos, validates current sources and exact approved previews, and preserves server-side EDIT_ITEM enforcement. No schema change.
- Full Maven gate passed: 1420 tests, 152 skipped, zero failures/errors. Focused owner-routing/parser and gallery tests passed; security and whitespace checks passed.
- Standard Windows UpdateOnly packaging completed for the current checkout at 1.1.97. verify-app-update confirmed the model-free ZIP; 168673795 bytes, SHA-256 69ee96d16078a680f41ea3f938cad6e88ad568861f1c7547b979f19945a3ee9d. No release metadata was published and no application/service was installed or restarted. Live server approval/promotion and storefront checks remain outstanding after installation is authorized.

## 2026-10-29 authorized 1.1.97 server installation and custom photo queue

- User explicitly authorized installing the prepared update. Transferred the exact 168673795-byte ZIP to POS-SERVER and verified SHA-256 69ee96d16078a680f41ea3f938cad6e88ad568861f1c7547b979f19945a3ee9d before running the standard updater.
- First attempt recovered to 1.1.96 because an orphaned 1.1.96 server Java process locked the service folder. Stopped that confirmed old server process and retried. Updater completed; installed desktop/service JAR hashes match the verified release. HTTPS health, installed version, local schema and LAN discovery passed. Server, storefront tunnel and scheduler tunnel tasks are running. All three persistent model hashes are unchanged.
- Preflighted all 42 current source image references/digests, then uploaded the unapproved custom-item batch into the existing shared Product Photo Review queue. Downloaded and compared all 42 originals and previews byte-for-byte; all decisions are review, and current primary photo references/digests are unchanged. Existing regular product reviews were retained.
- No release metadata was published. Approval/promotion awaits the user; live Swing approval and physical hardware checks were not performed.

## 2026-10-29 main custom-item photos for website service cards
- Added protected snapshot main-item photo manifests for sublimation, laser, business-card and large-format service catalogues. Public JSON exposes only image presence. Image lookup is restricted to items in the selected published snapshot; remote photo prefetch includes these main-item assets.
- Existing service cards now show the main item photo when available, with contained sizing on desktop/mobile. Missing or failed photos leave the existing text card. Shirt/plaque organization and variant designers retain their existing behavior.
- Website TypeScript/build passed; Edge browser acceptance passed for main photo, absent/404 photos and mobile width. ServiceProductPhotosTest, PlaqueCatalogTest, StorefrontImagePrefetchTest and StorefrontBundleTest passed; repository security and whitespace checks passed.
- Source and website bundle prepared only. Live activation requires a new immutable application release installed on POS-SERVER and the matching website assets deployed. Published 1.1.97 bytes were not changed.

## 2026-10-29 website service items grouped by main Item Type
- Storefront service and custom-category catalogues now expose the saved parent Item Type name. Each service renders alphabetized Item Type subsections with catalogue ordering preserved within each type. Blank types appear in Other items; no name-based guessing or database classification changes.
- Photo cards and plaque variant galleries share their parent's subsection. Existing service organization and designer workflows remain intact.
- Website TypeScript/build, 75 unit tests and Edge desktop/mobile acceptance (including grouped card counts, photo/failure fallbacks) passed. Targeted Maven and repository security/whitespace validation recorded for this change. Deployment remains pending alongside the main-item photo update; immutable published 1.1.97 is unchanged.

## 2026-10-29 Windows update 1.1.98 published
- Build 101098, standard UpdateOnly package. Includes main-item service photos and grouping by saved main Item Type; missing-template sublimation designers remain on hold.
- ZIP 168676562 bytes, SHA-256 24ec1a9c781468f8c7764007193832472012e1eaadf5ef474c5d8b0b8b1ce8b2. Standard protected publisher downloaded and verified exact remote bytes before release metadata insertion; independent metadata read-back passed. First-install selection unchanged.
- Maven package/full suite: 1422 tests, 0 failures/errors, 152 skipped. Website 75 tests and grouped photo-card Edge desktop/mobile check passed. Security and whitespace checks passed. Model-free ZIP verification and exact isolated updater acceptance passed, preserving model sentinel and previous-version backup. Source snapshot stayed unchanged during build.
- Publication only: no live install or service restart. Update POS-SERVER before registers; matching hosted website assets still need deployment. Physical printer/drawer/NFC and live installed service checks remain outstanding.

## 2026-10-30 missing template-size review queue
- Added Custom Inventory > Review missing template sizes: sublimation-first queue with optional other non-shirt inventory, per-variant targeting, photo and item details, existing partial dimensions, explicit units, Save & Next and Skip for now. Complete/inactive/archived targets are excluded; skipped entries remain incomplete and reappear on reopening.
- Added SAVE_TEMPLATE_SIZE in the authenticated catalogue-admin mutation path. It writes only width/height/unit with prior-value conflict checks and active target/parent ownership enforcement, under existing catalogue permissions/idempotency/transaction handling. Other item values and order-area settings are preserved. Images load off the Swing event thread.
- 17 targeted tests passed without skips, including real temporary-table PostgreSQL save/conflict/ownership checks. Disposable local PostgreSQL stopped afterward. Security and whitespace checks passed. Documentation: SmartStock/docs/template-size-review.md.
- Source implementation only; not packaged/published or installed. Live Swing dialog and server/register verification remain outstanding.

## 2026-10-30 Windows update 1.1.99 published
- Build 101099, standard UpdateOnly release with missing-template review queue and current combined SmartStock source. Existing first-install selection unchanged.
- ZIP 168689470 bytes, SHA-256 f574f0a81955e43c6a5a5b30394bbcda99ffb31f7fc6f96e82bbad7aab2b07f7. Protected standard publisher verified full remote download size/SHA before metadata insertion; independent metadata read-back matched. Published objects/previous versions were not modified.
- Full Maven package: 1427 tests, 0 failures/errors, 153 skipped (opt-in database test had passed separately during implementation). Security and whitespace checks passed. Model-free ZIP and hosted catalogue readiness verified. Exact isolated updater installed matching JAR, preserved existing model sentinel and previous-version backup. Release source hash snapshot unchanged through build.
- Publication only, no live installations or service restarts. Update POS-SERVER before registers. Live dialog/server/register and physical hardware checks remain outstanding. Matching hosted website assets for earlier photo/grouping changes still require separate deployment.

## 2026-10-30 live website photo/grouping frontend activated
- Diagnosed installed POS-SERVER already running 1.1.99 and returning main-item image indicators/types, while Cloudflare frontend overlay still referenced index-JNt70BV5.js/index-DSglL-22.css. Main photo endpoint returned HTTP 200 for magic cup.
- Downloaded current live Worker modules/settings, preserved gateway index.js byte-for-byte, existing registered origins/bindings/secrets/domains, and deployed exact storefront-web assets extracted from the immutable published 1.1.99 ZIP. Updated overlay JS/CSS references to index-E-OXMv0F.js/index-B9yNFEgt.css. Production preflight and protected origin-secret binding passed.
- Worker version 0d6afea3-1efa-4b86-bef3-3b81c990e0af. Live downloaded JS/CSS match exact release hashes. Edge browser verified 24 main-photo cards, 16 Item Type groups, decoded magic cup photo, zero page errors, and desktop/mobile width. No store data or installed service changed. Items with only variant photos and no parent photo remain without main-photo cards as requested.

## 2026-10-30 compact service cards live
- Scoped service Item Type grids use auto-fill columns so a short group no longer stretches one card across the page. Compact 200px photos, smaller copy/buttons, matching heights; two phone columns above 359px, single column on narrower screens. Type headings and plaque workflows preserved.
- Website build/TypeScript and synthetic service-page Edge acceptance passed, including same-row desktop cards, bounded single-card width and mobile fit. Live Edge check passed compact width, cups side-by-side, loaded photo, zero page errors and mobile width.
- Deployed matching frontend assets with existing gateway code unchanged. Cloudflare version fcbb2896-5618-4853-a211-08ed0fafc099; index-CoFUCzHw.js/index-CmswSTKb.css. App release 1.1.99 remains immutable; generated bundle updated for the next app release.

## 2026-10-30 compact Item Type sections share rows live
- Adjacent non-plaque sections pair when they fit up to four cards: 1+1, 1+2, 2+1, 2+2, 1+3 or 3+1. Weighted 12-column spans keep card widths consistent; maximum two sections per row. Bigger groups and plaque galleries keep full rows. Reading order preserved. At <=1000px sections stack while retaining compact card layout.
- Website build/TypeScript and 76 tests passed. Synthetic Edge verified two sections at the same vertical position on desktop and stacking on mobile. Live Edge verified BOTTLE/BRACELET share row, compact card widths, cups across, loaded photos, mobile fit and zero page errors.
- Frontend deployed with preserved gateway and bindings; Worker version a7c392fe-76eb-418b-8355-8cc679c6f075. Generated source bundle updated for next app release; immutable app releases unchanged.

## 2026-10-30 custom-item detail galleries and variant selectors
- Added click-through details for main custom service cards and individual plaque cards: main/additional/variant photo gallery, next/previous and thumbnails, bottom photo selectors, linked colour/size options using only real combinations, named shape/variant fallback, missing-photo state, and related plaque families/sibling variants with designer access.
- Storefront snapshot includes active variants/gallery manifests; public responses redact storage metadata and expose photo counts. Image requests are scoped by published main item, active variant and bounded photo index. Remote prefetch includes all gallery assets. General variants remain normal compact service cards; plaques retain separate cards.
- Website build and 79 tests passed. Edge detail workflow (colour, size, combined choices, photos, missing image, other plaques, designer), compact-card regression and all-nine-plaque regression passed. Targeted Java tests including real disposable PostgreSQL active/parent filtering and bundle verification passed; database stopped. Security/whitespace checks passed.
- Preview screenshots under SmartStock/target/ui-previews. Not published/deployed/installed; new server metadata and hosted frontend must activate together. Existing immutable app release unchanged.

## 2026-10-30 custom-item gallery live; 1.1.100 installed and published
- Standard Windows update build 101100: 168770921 bytes, SHA-256 34d8edd362b88d171ed048ccf0749dfecd59a5eac8e27757527100fc1dc9da44. Full Maven gate passed 1444 tests, 0 failures/errors, 154 skipped after adding null-category snapshot compatibility. Security/whitespace and isolated exact-ZIP upgrade passed. Source hash snapshot unchanged.
- Installed exact verified ZIP on POS-SERVER through standard updater; desktop/service JAR bytes verified against candidate. HTTPS health, version, local schema and LAN discovery verified by updater. All persistent model hashes unchanged; server/scheduler/storefront tasks Running. No other computers updated.
- Matching exact packaged frontend deployed with existing gateway/bindings preserved; Worker version 3923a4e2-4ab6-49cc-b538-9104b91a8887. Hosted JS/CSS hash matches packaged files. Live Edge verified Baby Cup four variants, kids bottle three variants, decoded colour-selected photos, main/gallery switching, mobile width, related nine plaques and designer entry. Public catalogue has no private image manifests.
- Standard R2 publisher downloaded/verified hosted ZIP size/SHA before release insertion; independent metadata read-back confirmed version/build/path/hash/size/published. First-install selection unchanged. Physical printer/drawer/NFC checks remain separate and were not performed.

## 2026-10-30 all-custom variant photo audit and first review batch
- User requested professional variant photos including shirts, with existing images retained as secondary after approval. Live authenticated catalogue audit: 397 active variants, 373 source photos, 24 missing source photos, 298 shirt variants. Twelve existing imported professional reviews retained (kids bottles, Baby Cups, stamps), leaving 361 source-backed variants initially pending.
- Generated six 20oz Speaker flask colour previews through built-in image generation. Uploaded original/preview pairs to existing shared catalogue review queue via authenticated API; no photos approved/promoted. Blue generation was held/rejected for invented speaker controls, regenerated faithfully, and corrected under expected revision=2/rejected guard. Six final shared JPEG previews and unchanged source photo URL/SHA verified.
- Local production batch evidence resides under ~/.smartstock/catalog-studio/variants-20261030 (not Git). Missing source list: SmartStock/docs/custom-variants-missing-photos.md. After first six, 355 source-backed variants remain; 24 need source photos. User's choice on reuse across visually identical shirt sizes is pending; avoid claiming entire catalogue done.

## 2026-10-30 shirt size photo reuse authorized
- User explicitly chose reuse across identical sizes. Grouping is scoped to the same custom main item, brand and colour; sizes retain individual variant targets, original bytes and source fingerprints, review decisions and secondary-image preservation on approval.
- Live audit groups 298 shirt variants into 122 parent/brand/colour groups; five groups have no variant source photo. Group membership is saved privately under ~/.smartstock/catalog-studio/variants-20261030/shirt-size-reuse-groups.json, subject to visual match review before reuse.
- First source group set: Army green Hanes (source 239), Black Pacific Polo Club (87), Black Hanes T-shirt (77), 14 individual variants. Each original/source hash captured; professional folded-product previews being prepared for shared review. Main product photos remain unchanged.
- First three reused shirt previews completed and uploaded through authenticated review API for 14 separate variants (4 Army green Hanes, 6 Black Pacific Polo Club, 4 Black Hanes). Generated PNG masters retained locally; server pending JPEG previews verified against uploads. All 14 original review bytes and current source hashes verified, no promotions. Total new variant reviews this run: 20 including six flask colours; 341 source-backed variants remain pending generation/reuse, and 24 lack individual source photos (some can be covered by a confirmed identical-size group). No claim that full catalogue processing is complete.

## 2026-11-09 temporary custom banner media fix
- Reproduced GP-4600S temporary 44 x 72 inch media loss using installed Canon driver and a no-spool Java harness: common Java dialog returned Letter; native dialog plus Java media reconstruction returned Poster 44 x 62.
- Banner printing now explicitly uses the native Windows print dialog, captures the native job/DC page before Java standard-media reconstruction, removes stale MediaSizeName/printable-area attributes, and preserves short-edge roll orientation. Too-small native media remains rejected.
- Live native driver check returned 1828.72 x 1117.52 mm after selecting temporary Custom Size 44 x 72, and passed banner size validation without creating a preset or submitting a print job. Physical printing remains unverified. No publication or live installation authorized/performed.
- Added regression coverage for native-dialog choice, stale standard media, tray/quality preservation, genuine size mismatch, and native landscape DC normalization. Existing unrelated working-tree changes preserved.
- Earlier in this chat, applied cloud migration smartstock_phone_minutes_cross_store_sync_functions through Supabase: added phone_minutes_sales to both cross-store function allowlists using existing invoker/service_role-only definitions. Verified both RPCs under service_role and all 12 table hash reads; live Skeldon status subsequently showed successful Rosehall refresh with no lastError.
- Final automatic-size workflow validated against Canon GP-4600S: choose printer, accept the auto-populated native Custom Size Page Setup, then print properties inherit 44.00 x 72.00 inches with no typing/preset. Using Book/Pageable instead of setPrintable avoids OpenBook's later standard-media remapping. Final no-spool harness passed resolved 1828.72 x 1117.52 mm, landscape, with no stale media attribute after final page attachment.
- Final targeted Maven test/package gate (BannerPrintingTest, BannerDocumentsTest, BannerArtworkTest, StudioPrintPipelineTest, PublicationPrintingTest) passed. Initial full suite passed 370 tests (1 skipped); a later full repeat was interrupted while an unrelated StudioTaskDialogTest waited on a modal dialog. No physical print job submitted.
- User explicitly authorized updating the installed app. Backed up installed 0.1.97 app/launcher under this chat work/SmartStudio-before-banner-fix. Built model-free standard UpdateOnly installer 0.1.99 (173604338 bytes, SHA-256 FCB224A7546F69F52C9C5C05EBBA71E7BFEAB3DC42FDE30052A1FEE92FD6E4CC), verified hash, and installed to the existing per-user SmartStudio directory; installer exit 0. Every installed app file matches the tested portable candidate and installed --check-launch passed. No release feed publication and no physical printing.

## 2026-11-09 SmartStudio 0.1.100 published
- User explicitly requested publication. Existing published 0.1.99 remains immutable; bumped Studio Maven version to 0.1.100/build 1100 and wrote release notes for automatic temporary banner custom media and Canon GP-4600S validation. No changes to the existing public first-install selection.
- Final targeted test/package gate: 32 tests, 0 failures/errors/skips; repository security and scoped whitespace checks passed. Standard UpdateOnly installer: 173604286 bytes, SHA-256 95ed249420e3c6eff2a4198199371ac7edf84934fad10f195b4e0c65e61f70db.
- Exact installer upgraded an isolated copy of installed 0.1.99; isolated startup passed and installed Studio JAR matches candidate hash 0816d83e2b2c86461ed054f9db6e6ec1a63c5ddc6deca75903c7df0a6d236ef9. File associations and existing shortcuts were backed up and restored after isolated validation; live installed app remains the previously authorized 0.1.99 local fix.
- Published using standard protected publish-verified-release-windows.ps1 -Kind studio. Hosted installer fully downloaded and verified before metadata publication. Immutable artifact path: smartstudio/windows/0.1.100/95ed249420e3c6eff2a4198199371ac7edf84934fad10f195b4e0c65e61f70db/SmartStudio-update-0.1.100.exe. No physical printing performed.

## 2026-11-12 admin-only custom-order item exchanges
- Added Orders > More > Exchange item, also available in detailed Items actions. Preview/save require an active ADMIN role on the local store server; remote admin and studio devices are blocked.
- Exchanges fixed-price custom-catalog stock items/variants one line at a time. Explicit return-to-stock choice, current replacement price, retained add-ons/line discount, configured order rounding, revised balance and any refund are previewed before confirmation. Required customer charge authorization is captured for an increased outstanding balance.
- Stock, parent quantities/sold counts, reservations, line identity/template dimensions, restarted production, financial history, refund, audit and outbox update together. Stock/price/order changes invalidate previews. Idempotency and a frozen retry request prevent duplicate stock issues/refunds after network interruptions.
- Delivered/returned, invoice/account-allocated, pending/area/customer-supplied/regular-product, loyalty-discounted and manually adjusted cases are explicitly rejected for their existing workflows. See SmartStock/docs/custom-order-item-exchanges.md for scope and acceptance instructions.
- Focused validation: 19 tests passed, including full-schema disposable PostgreSQL exchange/refund/authorization/rollback/concurrency/idempotency checks and Orders workspace/admin boundary checks. Dialog rendered and visually checked with the shared Orders typography/palette. Security and whitespace checks passed.
- Source changes only: no release publication, live installation, store database changes, provider refunds, or physical stock/cash/printer/NFC checks performed for this feature. Existing unrelated worktree changes preserved.
- Full Maven gate passed: 1480 tests, zero failures/errors, 160 optional tests skipped. Exchange-specific PostgreSQL coverage was separately enabled and passed.

## 2026-11-12 shared-workspace quick releases published
- Published SmartStock 1.1.113/build 101113 and SmartStudio 0.1.101/build 1101 using the standard protected publishers. Studio's SmartStock client dependency is 1.1.113. Includes the order exchange/payment reversal changes and current shared application/Studio resources, including full-page banner printing.
- Exact artifacts: SmartStock ZIP 169102133 bytes, SHA-256 97a9f40c528b6fba86d01db76539eb5f4ae96a8677ccbcfd7ca73ef2ccd1d60f; Studio updater 173658161 bytes, SHA-256 b117bfeee57b60bb60e8c4a1b9f3723ff76464103ee2ac8e99aa675d249dfe42. Full hosted download/hash/size verification preceded release metadata writes; read-back matched both release rows.
- SmartStock 1480 tests (160 optional skips), Studio 377 tests (one skip), gateway/mobile/portal 141 tests and storefront 79 tests passed without failures/errors. Security and whitespace gates passed. Website bundle rebuilt.
- Exact SmartStock update accepted over isolated 1.1.112; Studio updater accepted over isolated 0.1.99. Native startup, installed JAR identity, model exclusion and runtime/profile preservation passed; Studio registry/shortcut restoration was verified.
- Deployed storefront, Orders, Timeclock and installer portal Workers. Preserved storefront production origins/secrets and frontend overlay. Public HTTP checks and hosted JS/CSS byte verification passed. No model changes or full installer publication.
- No live desktop installation or store service restart. Server-first rollout and live database, hardware, operational exchange/refund and macOS checks remain outstanding. Existing unrelated working-tree changes preserved. Details: SmartStock/docs/release-1.1.113.md.

## 2026-11-12 approved Supabase updates, SmartDrive deferred
- User authorized all six outstanding cloud updates except SmartDrive. Applied the exact SQL from released SmartStock 1.1.113 for Sales Tasks permissions, Balance Sheet History permission, display device mode, Phone Minutes reversal permission/cross-store RPCs, one-time customer compatibility and order-payment reversal permission.
- Recorded all six immutable resource checksums/app version in SmartStock's migration ledger and refreshed the catalog/applied-resource checkpoints for the selectively applied set. The guarded one-time-customer change is a no-op in the cloud because public.custom_orders is not a live cloud table; no cloud POS/order table was created.
- Verified all four new permissions are granted to ADMIN with zero non-admin grants; DISPLAY is allowed; both cross-store RPCs remain SECURITY INVOKER and service_role-only. Executed both RPCs under service_role for all 12 supported tables successfully. All six ledger checksums and both checkpoints matched.
- SmartDrive permission and migration remain absent/pending at the user's request. The cloud is intentionally not claimed fully up to date while this migration is deferred. Existing unrelated working-tree changes and live store-local records were untouched.
- Supabase security advisor categories/counts were unchanged from preflight; no new advisory category was introduced by this update.

## 2026-11-13 SmartStock 1.1.114 published
- Reviewed recent product coverage, duplicate review, archive-cache, website pricing and NFC chats. Built from an isolated source snapshot and preserved unrelated working-tree edits. Shared Maven version is 1.1.114/build 101114; SmartStudio was not separately released.
- Includes active-product AI photo asset/status coverage and four ad frames with needs-ad filters, administrator duplicate review, archived/restored sale catalogue refresh, bundled website custom-item prices and NFC diagnostics. Ad generation/approval screens remain unfinished; hosted website activation/catalogue sync and the previously deferred SmartDrive cloud migration remain separate.
- Full Java gate: 1483 tests, zero failures/errors, 161 optional skips; initial missing sibling fixture corrected before a successful full rerun. Website 81 tests, TypeScript/build, repository security and whitespace passed. Standard all-in-one Windows packaging passed; model-free ZIP and hosted immutable model catalogue verified.
- Exact updater accepted over isolated 1.1.113: installed JAR/launcher identity, prior-version backup, runtime/model-profile preservation and native startup passed. Exact full installer accepted in isolated current-user directories; JAR/model/provisioner/startup checks passed. Existing shortcuts and prior uninstall registration restored and verified after correcting cleanup of protected common shortcuts.
- ZIP: 169144616 bytes, SHA-256 8f4aa214c6901506ae6ebb484187f04c203ba11f73a6b40a902aa96b9e77f16c. Full installer: 582211170 bytes, SHA-256 b58e1cb0ab1602534da51c97f26ca097d0cad5e30e51e3967ae640c810d4b917. Protected publishers fully downloaded and verified exact hosted bytes before selecting installer/inserting update metadata; independent manifest and release-row read-back matched. No prior release bytes or rows overwritten.
- No live app installation, database migration or store service restart. Server-first rollout, live database/register/sync acceptance, physical printer/drawer/NFC/camera, real model downloads/offline processing and macOS checks remain outstanding. Details: SmartStock/docs/release-1.1.114.md.

## 2026-11-14 cross-store inventory merge release 1.1.115
- Published model-free Windows update 1.1.115/build 101115 through the protected verified publisher. Exact ZIP 169178814 bytes, SHA-256 f129fe2214a44d6461ebf161261402b4ab376cc6597ea58083baf737034cecba. Full hosted download/hash/size verification preceded metadata publication; independent read-back matched. First-install selection and Studio channel unchanged.
- Added catalogue-UUID merge aliases, per-store stock consolidation and receipts, retained old barcode/SKU scan aliases, combined descriptions, original-photo preservation, historical money/cost preservation, stale-write retirement guards, photos/store quantities in review, and administrator stock requests executed by the owning store. Local baseline, guarded migration, recovery manifests and shared-reference sync updated; no new cloud POS stock table.
- Full Maven gate: 1486 test cases, zero failures/errors, 163 optional skips. Separately enabled two-store/replay/stock command/previous-schema drift and upgrade tests passed. Security, whitespace, exact updater/native installed acceptance and runtime/model-profile preservation checks passed.
- Installed the exact artifact on Skeldon via standard updater and verified service restart. Applied the three user-approved duplicate pairs there; original photos, all scan barcodes, merged descriptions, local stock conservation and retirement independently checked. Used the authenticated SSH maintenance channel after the original desktop login expired; did not renew or alter that session. Before-state evidence and approval context stay private on the server, outside Git.
- Confirmed six merge mapping/application announcements reached the shared cloud outbox with no sync failures/conflicts. Rosehall has not acknowledged them and still reports 1.1.113; a recent heartbeat does not mean compatible merge processing. Rosehall completion remains pending. Physical stock, hardware and macOS checks remain separate/outstanding. Details: SmartStock/docs/release-1.1.115.md.

2026-11-14: Published SmartStock 1.1.116 (101116), fixing image-reference reconciliation when asset metadata arrives later than its URL. Preserves existing links, gallery indexes and FK enforcement. Full suite with disposable PostgreSQL: 1487 tests, 0 failures/errors, 160 skips; security/diff checks, isolated exact-update/native startup and hosted download/hash/size checks passed. ZIP 169179086 bytes, SHA-256 1aeb7f731ec42fc8896a01bf2507f79b77bef10e0174a70d9ca7459ea3b1a2b4. Cloud metadata independently confirmed. No live store installation or Rosehall sync recovery verified; Rosehall must run 1.1.116 before remaining merge acknowledgements can be checked. No Supabase migration or model changes.

2026-11-14: Published SmartStock 1.1.117 (101117): verified catalogue photo identities reuse exact bucket/object rows with different store UUIDs and retain immutable local aliases in image_sync_state. Product imports and merge photo assignments resolve local identities without altering shared approvals or stock authority. Separate-store regression reproduces missing-photo merge, imports metadata despite newer item version, applies/replays once, retains shared fields and rejects object/checksum conflicts. Full suite: 1488 tests, 0 failures/errors, 160 skips; security/diff and isolated installed update/native startup passed. ZIP 169182778 bytes; SHA-256 fe4c38b0b281b8246f910ae3f57ea04198388d2160f3f6ec6f3c3ae357450b0f; hosted download and independent published metadata verified. Rosehall needs 1.1.117 before its two pending merges can be confirmed; no live installation performed. No schema or model changes.

2026-11-14: Rosehall confirmed 1.1.117 but pending book merges persisted. Read-only off-site metadata comparison identified the remaining guard: same bucket/path/category/access/SHA-256, but Skeldon provider SUPABASE and Rosehall ONEDRIVE. Published 1.1.118 (101118), permitting provider differences only with equal valid 64-character SHA-256 while retaining local provider/state and immutable shared approval. Regression now exercises provider migration, unknown-hash rejection, successful merge/replay and retained OneDrive metadata. Full suite 1488 tests, zero failures/errors, 160 skips; security/diff, isolated installed updater/native startup, hosted download/hash/size and independent published metadata passed. ZIP 169182776 bytes; SHA-256 84c01abf559233c95b1b33f0262f838b6b3d12941dc4ece20778043efd6cccc3. No live installation performed; Rosehall merge completion remains unverified until 1.1.118 runs. No schema/model changes.


## 2026-11-19 cumulative quick update 1.1.126
- Published SmartStock Windows 1.1.126/build 101126 from a frozen source snapshot. Includes the current completed shared Customers/account phone labels, Mobile Management, overseas/mobile and existing application changes. SmartStudio 0.1.105 source is unchanged; the separately deferred SmartDrive release stays excluded. No first-install selection, model publication or live installation.
- ZIP: 169394196 bytes, SHA-256 845552a733f2c9893dbe7ffcb488f09f7f1b56d7633f3b79c807d380a7d25e8f. Complete hosted download verified before release metadata; independent read-back matched.
- Combined Java reports: 1556 tests, zero failures/errors, 196 optional skips after adding a missing sibling test helper and rerunning the four affected tests. All 77 selected web/gateway tests, security/whitespace, model-free package/catalogue readiness and isolated exact-update/native-launcher/backup/runtime/model/profile acceptance passed.
- Deployed Orders, Inventory, Customers, Balance Sheet, Overseas and Time Clock gateways, retaining production variables. Root redirects and exact public asset hashes passed; the dedicated gateways also rejected invalid mobile methods/origins. Shared launchers return 404 until the live store server is updated; real-phone pairing and authenticated workflows remain unverified. Existing phones must pair again after server rollout.
- No live application/service/database change. Server-first rollout, real phone, local database/service, physical hardware and macOS acceptance remain outstanding. Details: SmartStock/docs/release-1.1.126.md; evidence outside Git in D:/SmartStock-release-builds/1.1.126.


## 2026-11-19 iPhone home-screen deployment 1.1.127

- Published verified Windows 1.1.127/build 101127 from frozen 1.1.126 plus the home-screen connection fix; unrelated newer work excluded. Standard model-free updater ZIP: 169401621 bytes, SHA-256 32fb98596aa629f54368e154d794339c1959fd2053e985eb3674e730542eb9df. Full uploaded object downloaded and checked before metadata publication; independent read-back matched. No first-install or model publication.
- Deployed all six staff gateways with current production variables/bindings retained and rollback versions recorded. Public pages/origin checks passed. Reconnect backend remains older (HTTP 405), requiring POS-SERVER update.
- Frozen build: 1558 tests, zero failures/errors, 198 optional skips; 53 focused web/gateway tests, security/diff, exact isolated updater/native startup and runtime/model/profile preservation passed.
- This PC is a production register pointing to POS-SERVER. Remote administration/admin share unavailable; no live installed app, service/task or store database changed. Required: on POS-SERVER, Status > Check for Updates > install 1.1.127, then verify the existing iPhone icons. Evidence outside Git: D:/SmartStock-release-builds/1.1.127. Details: SmartStock/docs/release-1.1.127.md.

## 2026-11-20 unified Deckers workspace deployment 1.1.128
- Built an immutable Windows update from frozen 1.1.127 plus the workspace changes, excluding unrelated newer source. Maven 1.1.128/build 101128. ZIP 169704112 bytes, SHA-256 a7577dff5a44b017e224beb5bff1fff52beb2ab48b6b9583973aee2900f13125. No release-feed metadata, first-install selection or model publication changed.
- Frozen package: 1564 Java cases, zero failures/errors, 199 optional skips; all 59 selected web/reconnection tests, security/diff, model-free/catalogue verification and exact isolated installed update/native startup/runtime/model/profile acceptance passed.
- Existing trusted SSH identity reached POS-SERVER. The complete ZIP was transferred and independently size/hash-verified, then applied using the standard updater. Installed desktop/service JARs match; server HTTPS health, installed version, local schema and discovery verified. Runtime and encrypted cloud credential retained; standard startup rewrote database.properties while retaining SERVER/store 1. Rollback retained.
- Deployed all six staff gateways with production settings retained and prior active versions saved. All 36 live public checks passed, including exact assets, role/device entry protections, blocked public LAN proof and legacy sites. Main URL: https://timeclock.deckers.gy/.
- Individual apps remain available. Await user iPhone Wi-Fi/mobile-data and authenticated workflow acceptance before retiring separate entry points. No physical hardware, macOS or register-install acceptance claimed. Details: SmartStock/docs/release-1.1.128.md. Private evidence: D:/SmartStock-release-builds/1.1.128.

## 2026-11-20 — Deckers single-login screen correction

Replaced public workspace tile navigation with in-document, scoped workflow
screens. The employee session now reaches the existing employee, mobile and
Scheduler services without a second screen login. Server-side app/device/role,
CSRF and direct-LAN proof checks remain mandatory. Order Design has a chooser;
Home retains native unsaved-change checks. Separate app entries remain pending
phone acceptance. See docs/deckers-unified-screen-qa.md and release-1.1.129.md.

Deployment complete: POS-SERVER and all six gateways are on the verified 1.1.129
correction. Frozen package tests 1566/0 failures/errors (199 skipped), selected
web tests 60/60, and live public checks 52/52. The model-free artifact and hosted
model catalogue were verified; normal updater/rollback/runtime/model-preservation
acceptance passed. Release-feed publication and individual-app removal were not
performed. iPhone and physical hardware acceptance remain outstanding.

## 2026-11-20 — Dedicated Deckers app address

Configured deckersapps.deckers.gy as the standalone employee workspace entry.
Prepared 1.1.131/build 101131 from frozen 1.1.130 plus exact-origin authentication,
mobile-management/LAN probe support and canonical QR links. Existing app screens
still mount in one document. The new Worker retains server origin enforcement and
returns SERVER_UPDATE_REQUIRED until the store server accepts this domain.

The user explicitly requested package/publication only and prohibited installation.
No live server update, service restart or installed-app update is performed here.
See docs/deckers-apps-domain.md and docs/release-1.1.131.md for verification receipts.

1.1.131 publication completed and verified: 169722837 bytes, SHA-256
9c393bcdf19a3c7c12cd2d0d47172a22e4e09b93eec163f4f3afd28e39a00bab.
1570 Maven tests reported zero failures/errors (200 skipped); 35 selected web
tests passed. Hosted models and the model-free archive were verified. The stored
artifact was downloaded and checked before release metadata creation. Dedicated
HTTPS domain/origin protection was verified. No live installation or service
restart was performed, in accordance with the user's instruction.

## 2026-11-20 — Device-specific QR re-pairing

Added Scan re-pair QR code directly below Use existing iPhone pairing in the
recovery sheet, plus an Account recovery entry. Mobile Management can issue a
single-use, ten-minute Re-pair QR for the selected approved device. Redemption
keeps that device ID and app grants, replaces this browser credential and clears
its login; it does not restore revoked devices or bypass employee permissions.
Camera, image and pasted-link paths are available, with scanner cleanup and URL
validation. Existing local handoff storage is reused without a schema migration.

Prepared 1.1.132 from frozen 1.1.131. Full Maven suite: 1571 tests, zero failures
or errors, 201 skipped; selected web suite: 39 passed. Real HTTPS/disposable DB
acceptance covered admin issuance/retry, same-device redemption, app preservation,
no duplicate device, fresh login, expired/reused/revoked QR rejection and non-admin
denial. Browser visual access was unavailable; real iPhone camera and layout
acceptance remain pending. No installation or live server restart is performed.

A final resource-content mismatch was caught before release metadata existed.
Publication of the first candidate was stopped; the standard package was rebuilt,
and exact recovery resource/class hashes were checked inside the final JAR/ZIP.
The unreferenced initial candidate is retained outside Git and is not the release.

1.1.132 is published and independently verified: 169726482 bytes, SHA-256
b24ea8669cde4fd9cd6cb9a8d9498b4169355912183327cb070caaee9d1dd0bb.
The final package matches its tested service classes and final recovery scripts.
Five legacy gateways were updated with their existing production configuration.
No live server/register installation, restart, first-install selection, or model
publication was performed. Real iPhone camera and layout acceptance remain pending.

## 2026-11-20 — Restore revoked mobile devices

Added a Restore device action on revoked rows in Mobile Management, using the
existing revoke-button position. The server requires Device Management permission,
current revision and revoked status, records the acting employee in the audit,
and handles retries idempotently. Existing app grants and browser credentials are
retained; neither apps nor names can be changed through restoration. Remote Admin
classifies approval issue/repair/restore as mutations requiring current store state.

Prepared 1.1.133 from frozen 1.1.132 with focused restore and remote-policy changes.
No live installation or server restart is performed. Nishan's earlier explicit
one-device restoration is separate from this software change.

1.1.133/build 101133 publication completed and was independently verified:
169727599 bytes; SHA-256 bb02fe1697ae42934ed522b477232139a6dc9db5f2508ad453e21b014e18f0dc.
The full suite passed (1571 tests, 201 skipped), then the final remote-policy and
restore change passed 15 focused tests (9 skipped); 39 web tests passed. Exact
packaged classes, model-free archive, hosted catalogue, security and whitespace
checks passed. No live installation, restart, first-install selection, model
publication or gateway deployment was performed.

## 2026-11-21 — cumulative quick updates 1.1.139 / 0.1.106
- Prepared frozen SmartStock 1.1.139/build 101139 and SmartStudio 0.1.106/build 1106 at D:/SmartStock-release-builds/1.1.139.
- Includes the unified Payroll screen/shared headers, photo framing, saved-password duplicate-submit fix, transfer drafts, queue error diagnostics and lossless Studio banner resampling/cache improvements.
- Main full tests: 1605 (0 failures/errors, 208 optional skips); Studio full tests: 383 (0 failures/errors, 1 optional skip); web tests: 47 pass; publisher tests: 3 pass. Security and whitespace passed. Isolated updater/native launch checks passed; no live installation or service restart.
- Cloud photo-framing migration and resource/catalogue checkpoint verified. Publication waited for Cloudflare model-catalogue availability, then verified the exact immutable catalogue. Protected publisher downloads each exact hosted artifact and verifies size/SHA before publishing metadata.
- SmartStock 1.1.139 published and independently read back. Studio publication status is recorded in D:/SmartStock-release-builds/1.1.139/publish-studio.log and release-verification.md.
- SmartStudio 0.1.106 publication completed and independent metadata readback matched its exact verified EXE. Both updates are published; neither is installed.

## 2026-11-23 — cumulative Windows quick update 1.1.141

Published SmartStock 1.1.141/build 101141 from D:/SmartStock-release-builds/1.1.141/source, combining this chat's iPhone login, payroll photo and scheduler changes with current other-chat source changes including digital overseas items, Store Keys and ad-review guides. SmartStudio has no source differences from published 0.1.106 and was not rebuilt.

Clean full suite: 1613 tests, zero failures/errors, 209 optional skips; 83 web tests and 3 publisher tests passed. Security, whitespace, exact packaged-resource, model-free archive and hosted catalogue checks passed. Isolated updater/native-launch acceptance passed; backup, launcher configuration and persistent model folder verified. Initial missing sibling test helper was corrected in the freeze before the clean full rerun.

Hosted download and independent release metadata verified: 170506170 bytes; SHA-256 2187772f50e141e537242790cd97b4b4c09c37e34d8224330d08ce0e5c3d8a2e. No live installation, restart, first-install selection, models or gateway deployment. Real iPhone, database/service and physical hardware acceptance remain separate. Full evidence: D:/SmartStock-release-builds/1.1.141/release-verification.md.

## 2026-11-23 — photo queue quick updates 1.1.142 / 0.1.107
- Published both verified Windows updates from frozen prior-release sources plus the queue fix. Full tests: SmartStock 1615, Studio 383; zero failures/errors. Isolated Stock updater/native launch and packaged Studio native launch passed.
- Both hosted artifacts fully downloaded and size/SHA verified before metadata creation; independent metadata matched. Evidence: D:/SmartStock-release-builds/1.1.142/release-verification.md. Live installs/services and first-install selections unchanged; update server first. Real phone/model and hardware acceptance remain outstanding.

## 2026-11-24 - cumulative Windows quick updates 1.1.143 / 0.1.108
Published both updates from the current shared-workspace snapshot at D:/SmartStock-release-builds/1.1.143/source, including the other chats' current SmartStock and SmartStudio changes. SmartStock 1646 tests (210 skipped), Studio 383 tests (1 skipped), zero failures/errors after correcting a missing sibling test helper in the snapshot. Website 83, provisioning 11, installer manifest 10 and archive validation 3 tests passed; security and whitespace passed. Exact Stock updater backup/config/model-preservation and isolated native launcher checks passed; Studio packaged native launch passed. Both hosted artifacts were fully downloaded and size/SHA verified before publication, then independent metadata readbacks matched. Evidence: D:/SmartStock-release-builds/1.1.143/release-verification.md. Windows update-only publication; no live installation/restart, first-install selection, model upload or Mac release. Update server first; physical hardware, real phones, store provisioning and live database/service checks remain separate.

## 2026-11-25 - cumulative Windows quick update 1.1.144
Published SmartStock 1.1.144/build 101144 from D:/SmartStock-release-builds/1.1.144/source, including the one-time authorized Rosehall custom-stock correction and the other chat's optional-NFC Store Keys checkout change. SmartStudio unchanged from 0.1.108, not rebuilt. Java 1654 tests: zero failures/errors after creating the missing disposable schema test DB and rerunning its check; 208 optional skips. Rosehall and eight Store Keys DB tests passed; website 83, provisioning 11, model publisher 5, archive validator 3 passed. Security/whitespace, exact packaged classes/SQL/JAR, model-free ZIP, hosted catalogue and isolated updater/native-launch acceptance passed. Hosted download verified before metadata creation; independent readback matched 170634886 bytes, SHA-256 a3226226fe00446c23d5003174ec1b60847dfd60d0897d6839b3605cc42db810. Windows update only; no live install/restart, model upload, first-install selection or Mac build. Update store servers before registers; Rosehall correction runs at first updated production-server schema readiness. Live correction/hardware checks remain outstanding. Evidence: D:/SmartStock-release-builds/1.1.144/release-verification.md.


## 2026-11-25 - cumulative Windows releases and first-install installers
Published SmartStock 1.1.145/build 101145 and SmartStudio 0.1.109/build 1109 from the frozen shared workspace in D:/SmartStock-release-builds/1.1.145/source. Includes the other chats' application changes, server validation of badge Front/Back pages, SmartStudio two-sided badge editing/output and whole-table movement/resizing. SmartStudio uses the 1.1.145 client libraries. Both in-app updates and both full installers are published; the public SmartStudio installer moved from 0.1.105 to 0.1.109.
Java suites: 2,039 tests, zero failures/errors, 211 optional skips; missing snapshot identity-helper fixture was supplied and its test rerun successfully. Storefront 83, installer portal 19, publisher/archive 8 tests passed, as did security and whitespace checks. Exact model-free archives, hosted immutable model catalogue, source fingerprints, installed full-app/native startup and updater/backup/model-directory preservation checks passed. All 11 pre-existing shortcuts and saved application registrations were restored and verified after isolated installer acceptance.
Each artifact's full hosted download, size and SHA-256 was verified before release metadata or installer selection changed. Independent metadata readback and full downloads from both permanent public installer links matched the exact local files. Public release mutation without the operator key was denied. Evidence and artifact hashes: D:/SmartStock-release-builds/1.1.145/release-verification.json and artifact-manifest.json. No Git staging/commit, live store install/restart, model upload or Mac publication was performed. Update servers before registers and SmartStudio; live database/service, real network, printer/drawer/NFC and macOS checks remain separate. Server template-pair consolidation still requires authenticated access to the live library.

## 2026-11-25 - Deckers phone intermittent LAN check
The shared workspace now retries a failed credential-free LAN probe once with a fresh one-use challenge and a 10-second timeout. Item operations are only sent after proof succeeds and are never retried by this change. Older background failures cannot overwrite newer successful connection status. Auth failures remain distinct and LAN enforcement stays server-side. All 55 web tests passed, including cold connection recovery, persistent denial, out-of-order checks, auth/address validation and gateway isolation. Deployed canonical app.js through the existing Deckers gateway asset binding on its exact GET/HEAD script route with no-store. APIs still go to the authenticated store server; the local probe is never proxied. Worker version 68837832-8138-481b-8659-b9c307e1d2d0. Live script SHA-256 matched source after revalidation: 7d1e109a2d823f55288bdc6abd8b0488b8664631dd5a9d12ffb35ad5fbda6dda. Trusted LAN HTTPS returned 200/TLS verify 0; direct public probe returned 404. Real iPhone acceptance pending after app close/reopen. No server/register restart or installation.

## 2026-11-25 - cumulative Windows Quick Updates 1.1.146 and 0.1.110
Reverified the already-published SmartStock 1.1.146/build 101146 against the current shared SmartStock release inputs and exact release metadata. Published SmartStudio 0.1.110/build 1110 with negative horizontal and vertical business-card spacing and the exact SmartStock 1.1.146 client. Studio ran 389 tests with zero failures or errors and one skip. The exact updater upgraded the isolated 0.1.109 installation, retained its runtime, contained no model weights, and passed native startup. Hosted download size and SHA-256 were verified before metadata publication and matched the independent readback. Evidence: D:/SmartStock-release-builds/studio-0.1.110/studio-published-metadata.json. Live store, macOS, and hardware checks remain separate.


## 2026-11-25 - live badge template consolidation
Merged 14 badge designs into Front/Back templates on the authenticated POS-SERVER library, including Queensway standard/Serif, Orealla permit and both NDC variants. Renamed 60 separate-side/duplicate entries with ZZ Archive prefixes. Preserved all 272 original template IDs and store assignments, retaining existing references because the print-history API returned a server error. The 198 unrelated templates were unchanged. Every saved page was read back and its rendered pixels verified against the prepared merge. Private source backups and the operation journal are outside Git under the user profile .smartstudio/maintenance/badge-pairs-20261125. No application rebuild/publication was needed.


## 2026-11-25 - SmartStudio 0.1.111 visible badge controls
Published Windows SmartStudio 0.1.111/build 1111 updater and full installer. Insert now provides Blank back and Add back from template; View provides Front/Back switching. Persistent controls above the card remain visible with a collapsed ribbon. Replacing existing Back artwork with a blank page requires confirmation and preserves Front artwork. The frozen build starts from published 0.1.110, retains its card-spacing changes and exact SmartStock 1.1.146 client dependency. Version 0.1.110 was not overwritten; its original release notes were retained.
390 tests passed with zero failures/errors and one skip after supplying the PDF integration test source fixture; security/whitespace passed. Expanded/compact UI renders, exact isolated 0.1.110-to-0.1.111 updater/full-installer acceptance, native startup, runtime preservation and model-free payload checks passed. Existing shortcuts and application registrations were restored and read back. Full hosted bytes, sizes and SHA-256 were verified before update metadata/public installer selection, and the permanent public Studio installer was independently downloaded and verified. No live installation, backend release, template-data mutation, model upload or Mac publication was performed for this UI fix. Physical printing remains a separate check. Evidence: D:/SmartStudio-release-0.1.111/release-verification.json.
