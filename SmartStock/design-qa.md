# Sales Tasks option 3 design QA

Date: 2026-10-25

final result: passed

Scope: implement the selected option 3 in the production Java/Swing Sales Tasks workspace, preserving its workflow and configured branding. This result covers rendered production components and focused interactions; installed applications and physical hardware are separate checks.

## Evidence

- Reference: `docs/sales-task-design/reference.png` (1488 × 1056, including window chrome).
- Final production-component renders: `target/sales-task-design/fidelity-final/workspace-1488.png` (1488 × 987 content), `workspace-1024.png` (1024 × 768), `customer.png`, `pricing.png`, `pickup.png`, and `dark.png`.
- `target/sales-task-design/fidelity-final/comparison.png` places the reference and implementation side by side at the same content viewport and Work & items state, removing only the reference's native window/menu chrome.
- `render-provenance.txt` identifies the isolated compiled classes used by these renders. The comparison and each workflow stage were inspected, including the compact and dark layouts.

## Result

- Matched the task rail width and card density, company header, status badges, chevron steps with numbered/completed markers, grouped work and inventory cards, inline attachment actions, table columns and row actions, summary strip, and full-width footer.
- Configured company name/logo and shared Deckers palette remain connected to application settings. Test captures use the bundled logo and fictional records.
- Inventory selection supplies the catalogue price; provisional quantities remain editable. Unknown balance reads “Not finalized.” ASAP remains separate from the date/time selector.
- Stage controls prevent pickup before final pricing. Finalized charge lines are read-only. Existing authenticated LAN operations handle payments, approvals, transitions, and collection.
- Fixed excessive vertical expansion and clipped summary notes. Smaller windows scroll the content, wrap the summary into two rows, and preserve footer access. Keyboard actions are available for charge lines and attachments.
- Segoe UI typography, the application's Deckers palette, and bundled Font Awesome Free icons implement the reference in Swing. The icon font and its license are included in packaged resources.
- The shared theme pass now preserves custom button painting and updates the table, cards, fields, and summary colors when an existing workspace switches to dark mode. A regression test checks readability and that the primary action stays inside the window.
- Currency follows the existing application formatter; customer account numbers, attachment sizes, deadlines, and tender summaries use actual records. The reference's assertion that physical originals are held is replaced with truthful attachment guidance because no physical-original custody flag exists.
- No unresolved P0/P1/P2 issues were observed in the inspected final renders. Native widget rendering and font antialiasing can differ by platform.

## Validation and limits

- Full Maven suite passed using an isolated output directory (1330 tests, 0 failures/errors; 142 opt-in/platform skips). A prior shared-output run was invalidated by concurrent compilation; its missing-class failures were not treated as code failures.
- Final focused checks passed, including all six workspace cases: inventory quantities/prices, duplicate-name inventory selection, new-task/customer isolation, pricing/pickup locks, responsive renders, and switching an existing screen to dark mode. Customer/date controls, task rules, receipts, and Orders queue checks also passed.
- All 16 opt-in SalesTaskDatabaseTest cases passed on a disposable loopback PostgreSQL cluster, including account-detail retrieval. The test cluster was stopped after validation.
- Bash security check and full-tree `git diff --check` passed. New UI files were also checked with `git diff --no-index --check`.
- Installed-app migration, live store operation, and physical printer/drawer behavior remain separate manual checks. No release was packaged, published, or installed for this redesign.

## Quotations and invoices — 2026-11-23

final result: passed

The selected Document Studio direction is implemented in the native Swing workflow with existing Grid/Compact quotation and invoice formats and the existing delivery-note format. See `docs/quotation-invoice-design/design-qa.md` for source/render comparison, responsive evidence, test results and manual acceptance limits. Evidence is under `target/document-studio/`.


## Quotations and invoices — 2026-11-24 correction

Corrected layout verified in offline native Swing renders. See docs/quotation-invoice-design/correction-qa.md for evidence, intentional adaptations, checks and installed-app limitations.
