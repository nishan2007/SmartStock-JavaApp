# SmartStock 1.1.105 Windows quick update

Build 101105, prepared 3 November 2026. Corrects unpaid-account statements that
included invoices never charged to the account. Excludes cancelled invoices and
custom orders billed on an invoice; reconciles additional charges against the
connected store ledger. No customer debt or payment is changed by this fix.

Letter preview and printing now share a paginated US Letter renderer, with
aligned columns, repeated headings, page numbers and the total on the final page.
The corrected one-page school statement and multipage layout were rendered and
visually checked during implementation.

- ZIP: `smartstock-windows-1.1.105.zip`, 168802848 bytes.
- SHA-256: `ec0bb039d27d22c7ac8b2197bacd89eb9455cda6ffcabd892b9f5e4ac5692143`.
- Full Maven suite: 1455 tests, zero failures/errors, 155 skipped. The balance
  regression integration test ran successfully against disposable PostgreSQL.
- Security and whitespace checks passed; model-free archive verification passed.
- Applied the exact ZIP through its updater over an isolated 1.1.104 app.
  Verified installed JAR identity, superseded JAR removal, native launcher config,
  preserved runtime/profile content, model exclusion and native welcome startup.
- The first packaging pass stalled on a GitHub tunnel-runtime download after
  tests completed. Resumed standard packaging using its supported pinned-binary
  input, verified against the required SHA-256. Completed tests were not repeated.
- Published 3 November 2026 after complete hosted download, size and SHA-256
  verification. Independent metadata read-back confirmed version/build,
  Windows platform, published status, object path, size and hash.

Update the store server before registers: the balance query correction is
server-side. Windows application update only; no live installation, service
restart, first-install selection, SmartStudio release or hosted web deployment.
Physical printing, live server/register behavior and macOS checks remain rollout
steps. AI models remain separate and preserved.
