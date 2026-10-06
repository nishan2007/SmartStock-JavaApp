# SmartStock 1.0.238 Windows release

Prepared from the complete frozen application source in `target/release-source-1.0.238`, with a SHA-256 source manifest. No live working tree, machine-local configuration, production credentials, database dumps or recovery evidence are published.

## Included

- Separate Ethernet defaults for custom-order slips and barcode order labels, including a cut after each label copy. The slip preview follows the selected default; explicit installed queues remain available.
- Administrator Web Status for the public website, installed tunnels, scheduler, employee applications, mobile tools and LAN API. Fixed service controls require server-side authorization.
- Shared other-store inventory, sales and customer-history snapshots, table fingerprints, changed-row downloads, measured cloud transfer totals, billing-period settings and refresh diagnostics.
- All current custom-order spoils, attachments, Studio, storefront, payroll, employee/device, register, recovery and updater improvements in the source.
- Both pinned offline photo models, verified Cloudflare runtime, Java runtime, PostgreSQL installer and ordered local/cloud database migration resources.

## Artifacts

- Windows update ZIP: `smartstock-windows-1.0.238.zip`, 1,114,041,418 bytes; SHA-256 `abb085451881079725455aa80c439f9d4ab264f9d38b84eb819e24eeb6d5ed97`.
- All-in-one Windows installer: `smartstock-windows-setup-1.0.238.exe`, 1,520,467,527 bytes; SHA-256 `199ecc61bf85d681d9b1b21b63a85e99013e41d677b2bfd47215efef210c3ef8`.
- Application JAR SHA-256: `04770ac0633b5eb8c53460b255bccb840ef935fc9502ba4e2c6efa757e2a5637`.

## Validation

- Frozen Maven package suite: 803 tests, zero failures or errors, 43 optional skips.
- Three additional cross-store database tests passed on an isolated loopback PostgreSQL cluster. No production local database was used.
- Exact 1.0.237 JAR installed a disposable local schema; exact 1.0.238 JAR upgraded it and validated the resulting contract.
- Repository security and whitespace checks passed.
- Previous official Windows installer installed in an isolated fixture. Its real updater, run from a separate staging JAR, applied the exact 1.0.238 ZIP. All 25 payload files matched the archive; both native launcher configurations reference the new JAR.
- Updated fixture launched through the native executable and bundled Java runtime with an isolated home in client mode. Startup diagnostics confirmed the Welcome screen was visible.

- Fresh 1.0.238 all-in-one installer installed in a second isolated fixture. All updater payload files matched, native launcher configs and Java launchers were present, the PostgreSQL installer matched its pinned hash, and the native Welcome screen opened through the bundled runtime.
- Both fixtures were uninstalled with their official uninstallers; ignored updater/test evidence is retained locally; the disposable PostgreSQL cluster was stopped. The existing production installation was not replaced.

## Cloud deployment

Applied the packaged cross-store egress migration to the configured production project before publication. The guarded transaction verified the previous catalog, resource fingerprint, migration count and immutable historical checksums. The known historical builtin-permission checksum was accepted using the migration runner's compatibility rule.

Independent readback confirmed 25 migration records, completed fingerprint backfills, and two server-only RPCs denying anonymous and authenticated execution.

- Resource fingerprint: `fcd6600845ee48104401294ed3104cec7d02c4e4ec27ae76ddb0a084b7a007d6`.
- Catalog fingerprint: `8d40098ca7f9d17ba5bb2bbb1a4768cf09cf74f0e24a08cc4444e361c5d39f46`; independently recomputed and matched the checkpoint.

Existing advisor notices remain for server-only RLS tables without client policies, the employee-activation trigger search path, authenticated permission helper functions, and disabled leaked-password protection. Their remediation references are [RLS policies](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy), [function search paths](https://supabase.com/docs/guides/database/database-linter?lint=0011_function_search_path_mutable), [authenticated security-definer functions](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable) and [password protection](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection).

## Deployment checks

Update store servers before registers. Live server/service control, paired-device flows, two-store freshness/workflows, production backup/restore, physical printers/cash drawers/NFC and production update application remain separate acceptance checks. No macOS artifact was built; no SmartStudio companion release was needed for the current desktop changes.

The Windows in-app update was published as build 100238 on October 3, 2026 after the complete stored ZIP was downloaded and its size and SHA-256 matched. Independent update-feed readback matched the exact artifact path, hash, size and published status. The matching all-in-one installer was also fully downloaded and verified before the portal selection changed. The permanent account-protected Windows installer address is https://downloads.deckers.gy/download/windows.
