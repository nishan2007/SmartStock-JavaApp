# SmartStock 1.0.237 Windows update

Prepared from the complete SmartStock source snapshot at
`target/release-source-1.0.237`, with a SHA-256 manifest covering 1,267 files.
The custom-order spoil work is included in this release. The published
SmartStudio 0.1.2 companion remains compatible and does not need a new installer.

## Included

- Custom-order button contrast fixes and attachments before adding cart lines,
  with attachment editing afterward.
- Internal phone QR spoil reporting, required photos and reasons, exact item or
  variant replacement stock deduction, shortage warnings, retry protection,
  desktop evidence history and permission-controlled reversals.
- The completed register, staff, payroll, Studio, storefront, local-server,
  recovery and updater changes present in the frozen source.
- Both pinned offline photo models and the verified Cloudflare tunnel runtime.
- Local schema installation/upgrade resources and the separate cloud permission
  migration. Operational spoil tables and photographs remain local; private
  cloud store-row recovery includes them.
- Corrected fresh cloud provisioning so register-only tables are not required.

## Artifact

`target/release-windows-1.0.237/smartstock-windows-1.0.237.zip`

- Bytes: **1,113,984,211**
- SHA-256: `a2aa1dcf138ebde4db15b6955d76d057638835d918bd5238a5db83678da5cd2f`
- Main JAR SHA-256: `f586e83522189a48edb5734128a45ef9c7dbd5bd1c1b277b15241830c7ef55bb`

This is an in-app Windows update ZIP. No new full installer or macOS artifact
was built. Version 1.0.237 (build 100237) was published to the production
Windows update feed on October 2, 2026 after the entire stored artifact was
downloaded and its size and SHA-256 matched the verified local release.

Published object:
`updates/windows/1.0.237/a2aa1dcf138ebde4db15b6955d76d057638835d918bd5238a5db83678da5cd2f/smartstock-windows-1.0.237.zip`.

## Supabase production update

Applied to the configured SmartStock-Deckers production project:

1. Store-mirror clone timeout.
2. Payroll event ownership filtering.
3. Studio device access column and index.
4. Record/reverse custom-order spoil permissions and administrator grants.

The two sync function definitions already matched the pending migration scripts,
but their application history was absent. The live catalog was compared with a
fresh disposable candidate; existing function differences were line endings.
The guarded transaction checked the reviewed live catalog, migration count and
every existing immutable post-v1 checksum before applying the four scripts,
recording their exact checksums and refreshing the contract checkpoint.

Independent readback confirms 24 applied migration records and both permission
keys. The exact release JAR reports the same cloud resource fingerprint as the
production checkpoint:
`c6a44bea18aa4f455b3a0dcc1e3017931ee01d3d9c6c5735ca6cc816867ce2e8`.
The independently recomputed catalog matches the saved checkpoint:
`ba2ceb8e77caebcf1dc28bbe4df4ecff1e68a9e50b6cc172b751f0ca6c38175a`.
Live spoil tables were not created in the cloud. Server sync RPCs deny execution
to anonymous and authenticated clients; private recovery tables retain RLS.

The Supabase advisor reports existing informational notices for intentionally
server-only tables without client policies, and existing warnings for the
[employee activation trigger search path](https://supabase.com/docs/guides/database/database-linter?lint=0011_function_search_path_mutable),
[authenticated permission helper RPCs](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable),
and [disabled leaked-password protection](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection).
These existing settings were not changed as part of this release.

## Verification

- Frozen package Maven suite: 785 tests, zero failures/errors, 40 optional skips.
- Separate disposable PostgreSQL spoil tests: stock, variant handling, photos,
  transaction rollback, concurrency, retries, reversal and client isolation.
- Full fresh cloud baseline plus all migrations installed and validated in
  disposable Supabase emulation.
- The exact previous 1.0.236 JAR installed a disposable local schema; the exact
  1.0.237 JAR upgraded it and validated the resulting contract and spoil tables.
- Phone browser checks passed for required photos, preview, shortage warnings,
  persistent retry identifiers, internal viewing and manager reversal.
- Repository security check and whitespace validation passed.
- Exact archive content and both model hashes checked; both offline quality
  modes ran successfully with transparency and original image dimensions.
- The previous installer installed into an isolated workspace fixture. Its
  real updater applied the exact 1.0.237 ZIP from a separate updater staging
  path. The installed JAR matches the archive and both native launcher configs
  reference 1.0.237. The bundled-runtime native launcher opened the Welcome
  screen with an isolated home and client mode.
- The test installation was removed with its official uninstaller after the
  checks. The disposable PostgreSQL cluster was stopped. Release artifacts,
  checksums and recovery evidence for the test fixture remain in the release
  output directory.

Update the store server before registers. Production update application, live
phone pairing/certificate trust, installed services, database recovery and
printers, drawers and NFC remain separate live checks.
