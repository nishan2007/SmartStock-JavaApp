# SmartStock 1.1.9 — Readable custom-order details

Adds a reusable order-details summary with customer, status, due date, assignment and prominent financial totals. Items & Notes shows compact cards with separate production and delivery statuses. Payments shows a table and payment controls, disabled for paid and invoice-linked orders. Activity shows expandable audit entries and operational delivery/production/return events with readable dates. Print actions are grouped and controls wrap at narrower widths.

Authenticated DETAILS_V2 provides structured, store-scoped data. The legacy DETAILS text response remains available. Request generation tokens reject stale responses; refresh preserves the selected order. No database migration is required.

Validation: Maven packaging suite passed (876 tests, zero failures/errors, 47 skipped), including paid/invoice-linked state, stale-response and history tests. Repository security and whitespace checks passed. The structured query passed a read-only local database check and rejected the wrong store. Visual checks at 600 and 940 pixels passed. Model-free archive verification passed; the archive JAR matches the tested JAR. An isolated extracted update using the installed native launcher/runtime opened the welcome window with a temporary development profile; the process was stopped.

Artifact: `smartstock-windows-1.1.9.zip`, 133,312,236 bytes; SHA-256 `9fc4150e1e8794d92883e11cb8a988f4dcfcdafe402e4fdef807029dd1e3e73d`. Windows build 101009. The verified publisher checks hosted model availability and downloads the complete stored artifact before release metadata publication.

Update-only release; no new installer or macOS package. Update the server before registers for the structured details API. Installed-app update, live payment/delivery/refund flows, database/services, backup/restore, printers, cash drawers and NFC remain separate verification steps. Publication does not automatically update running installations.

Publication completed: full stored download matched the byte size and SHA-256 above before metadata insertion. Published response matched Windows update 1.1.9, build 101009, and its verified artifact.
