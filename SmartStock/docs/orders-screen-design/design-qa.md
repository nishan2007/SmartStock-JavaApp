# Custom Orders screen verification

Date: 25 October 2026

26 October follow-up: widened the queue to 40% of the window with a 660-pixel minimum, a draggable divider, and a 225-pixel order-number column. The divider is positioned again after the frame reaches its final size, preventing the initial 400-pixel layout from persisting. The default **Current orders** scope excludes delivered orders with a settled balance; delivered orders with outstanding or pending prices remain visible. **All statuses** and explicit status filters retain access to history. Scope counts use the same current/history predicate. Ten workspace tests cover these rules and resizing, alongside the existing details/column checks; targeted Maven tests, security checks and whitespace validation passed. No installed-app, live-store or hardware changes were performed for this follow-up.

final result: passed

This result covers the renovated Java/Swing screen, its connection to the existing order handlers, and disposable-database verification. It does not certify an installed store application or physical hardware. The separate Sales Tasks report at `SmartStock/design-qa.md` was preserved.

## Reference and evidence

The supplied 1490 × 1056 reference is the visual target. Native Windows captures at a 1490 × 1056 frame produce 1476 × 1049 captured pixels after the invisible frame margins. The comparison normalizes the reference to that capture size; the selected order, theme and Items tab match.

Evidence under `SmartStock/target/orders-design/`:

- `implementation-final.png`: final reference-size native Swing preview.
- `comparison-final.png`: reference and final implementation together, inspected at full size.
- `comparison-details.png`: focused production, financial and action-bar comparison from the preceding alignment pass.
- `compact-top-fixed.png`, `compact-bottom-fixed.png`, `compact-final.png`: 1280 × 760 window checks, including reaching payment and every bottom action by scrolling.
- `files-tab.png` and `more-menu.png`: native navigation checks.
- `source-provenance.json`: source hashes used during verification.
- `final-native-full-tests.log`, `final-focused-tests.log`, `final-post-layout-tests.log`, `final-visual-regression-tests.log`: validation logs.

The preview uses fictional orders based on the supplied example. Counts, other orders' dates and production states differ from the reference. Its product photograph is a reference crop confined to the ignored preview directory; production previews load actual order media. The preview contains no design-proof records, so it does not fabricate the reference's green approval banner. The production screen displays approval from the latest stored proofs.

## Fidelity review

| Surface | Result |
| --- | --- |
| Typography | Bundled, licensed Roboto Condensed for dense order content; Segoe UI for the application heading and order number. Full names fit the reference-size list, with tooltips and adaptive sizing for longer records. |
| Layout | Matched the approximately 39.5% list rail, brand header, search and scopes, five filters, five visible list columns, three workflow cards, four detail tabs, item grid and preview, instructions and attachments, payment and totals, and bottom action bar. |
| Colors | Navy text, pale blue borders/table headers, semantic badges, pale orange totals and selected rows, and the existing shared Deckers orange. Dark-theme field/caret and selection contrast are preserved. |
| Images and icons | Existing Deckers logo and the application's licensed Font Awesome icon font. Actual media is loaded asynchronously through the authenticated LAN API, with a bounded thumbnail download and selection-generation protection. |
| Copy and behavior | Real store, staff and date; actual counts and prices; invoice billing and pending prices distinguished; loyalty discounts shown when present; no fabricated collection or proof approval. |

Earlier blocking findings were fixed and recaptured: native Windows buttons obscuring the primary fill; equal-width workflow cards; a wrapping full-size toolbar; excessive notes height; generic Files-tab help instead of records; inline payment figures instead of right-aligned amounts; incorrect total emphasis; uniform payment-field widths; and compact tables clipping order numbers and financial amounts. Narrow tables now scroll, while the right workspace scrolls vertically in short windows.

Remaining minor native-platform differences are the exact border radii, progress-marker fill, native combo-box arrows and font rasterization. Compact card titles can ellipsize while their icon and status remain visible. These do not hide an action or alter a workflow.

Two functional additions to the mock are intentional: **Apply Payment** explicitly submits the form, and **Refresh** reloads the list. Extra order actions are under **More**, as requested. Work Queue is a separate screen.

## Functional coverage

- Search calls the complete, store-scoped order list, preserving all historical orders and avoiding the former lookup limit.
- All/My/Unassigned scopes, status, per-line production/delivery, staff and payment filters, page sizes, page navigation and global sorting work together. My Orders uses assigned user IDs when available.
- Selection survives filtering/pagination where possible. Empty, loading and failed detail states clear stale content and disable payment. Old responses cannot overwrite the current order.
- New Order opens the existing creation/held-cart workflow and refreshes after closing. Payments retain the existing authenticated mutation, reference rules, idempotency and server financial enforcement.
- Production, files/design approval, delivery/calls, slip preview, slip printing and label reprinting retain their established handlers. Files and the latest proof revision are visible in the Files tab; full file/proof actions open the existing management dialog.
- More retains detailed lines, loyalty redemption, account authorization, refunds/returns, cancellation, price finalization, spoil history/phone QR and permitted Made at Deckers drafts.
- Assignment uses the existing server endpoint and captures the original order state before loading employee options, avoiding changes to another order after selection moves.
- Unit prices come from saved unit prices rather than line totals. Invoice-billed orders cannot accept a duplicate payment. Loyalty discounts remain visible in the financial summary.
- The disposable PostgreSQL test verifies real SQL results, location isolation, assigned identity, mixed line states, exclusion of fully returned lines from filters, and distinct unit prices and line totals.

## Validation

An independent Maven build directory was used because another Maven run was rebuilding the shared `target` output during testing. The generated validation POM retains this repository's dependencies, compiler, source/resources and Surefire configuration, changing only build output and test working-directory paths.

- Full native Swing suite: **1,346 tests; 1,210 passed, 136 skipped, zero failures, zero errors**. Run with `-Djava.awt.headless=false`; the default headless configuration cannot create the native peer used by the separate Work Queue rendering test. That test also passes independently in native mode.
- Seven workspace regression tests pass, including action dispatch to the selected order, global pagination/sorting, financial filters, user identity, stale response handling, actual unit prices/invoice billing/loyalty totals and latest-proof selection.
- `OrdersWorkspaceDatabaseTest` passes against the explicitly named disposable `orders_workspace_test` database on loopback. The test instance was stopped afterward.
- Existing custom-order details, column, workflow, creation, printing and service tests were included in the focused/full runs. Focused checks were repeated after the final layout changes.
- `SmartStock/tools/security-check.sh` through Git Bash: passed.
- `git diff --check`: passed.
- Final native preview error log: empty.

No release was published, application installed, live order changed, or live database migrated. Installed-app behavior, live Windows service and LAN operation, real payment/cash-drawer behavior, physical printing and NFC remain deployment/hardware acceptance steps. Automated tests and the native fixture preview do not certify those checks.
