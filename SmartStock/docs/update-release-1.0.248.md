# SmartStock 1.0.248 — quotation and invoice print layout

Windows update containing the current application source and resources, including the changes released in 1.0.247.

Quotation and invoice line tables show quantity, description, unit price, original total (quantity times unit price), discount percent, and final amount. Description receives 42% of table width. Subtotal and grand total are stacked at the bottom. Subtotal sums final line amounts with cents; grand total rounds half-up to whole currency units. A subtotal of 279,000.11 displays grand total 279,000. Stored invoice totals, payments, and balances retain their existing precision. Delivery document columns remain unchanged.

Validation: full Maven packaging suite passed (841 tests, zero failures, zero errors, 46 skipped); security and whitespace checks passed. The packaged JAR successfully opened its welcome/setup window using an isolated temporary user home, without connecting to a production store database. The document preview was generated from the packaged JAR. The ZIP application JAR SHA-256 matches the tested JAR. The packaging script verified archive paths, runtime dependencies, pinned cloudflared, and offline image-processing models.

Update artifact: `smartstock-windows-1.0.248.zip`, 1,114,343,685 bytes; SHA-256 `861a94dff25777cb7dca186892e84347203baf84a260d57e861630a1599bec60`.

Publication is performed by the protected verified release publisher, which downloads the complete stored artifact and checks size and SHA-256 before inserting release metadata.

This is an update ZIP release. No new installer or macOS artifact was built. Applying the update to an installed app, production database flows, backup/restore, Windows services, printers, cash drawers, and NFC hardware remain separate live verification steps. Publication does not update running store installations automatically.

Publication completed: full storage download verified 1,114,343,685 bytes and the SHA-256 above before release metadata insertion. Readback confirmed Windows version 1.0.248, build 100248, published status, size, and hash.
