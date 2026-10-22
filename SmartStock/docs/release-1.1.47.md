# SmartStock 1.1.47 (build 101047)

Windows application update only, built from the current shared workspace. Includes
the cross-store catalog identity/reference mapping fix, verified product mapping
for cross-store return restocking, the other chat's OneDrive reconciliation
timeout fix, and the existing shared-workspace changes. The standalone SmartStudio
application retains its separate release channel.

Older installed Windows runtimes without `jdk.management` now use Windows native
available-memory reporting for background removal and enhancement. This update
does not replace the installed Java runtime.

Validation:

- Full Maven package/test gate: 1,110 tests, zero failures/errors, 78 skips.
- Catalog identity/import/migration tests passed separately against a disposable
  PostgreSQL database before release preparation (18 tests, no skips).
- Windows native memory fallback test passed.
- Repository security check and `git diff --check` passed.
- Final ZIP passed the model-free archive and hosted model catalogue checks.
- Final ZIP extracted into an isolated validation folder. Its memory reporting,
  authenticated status resource helper and catalog identity class loaded using
  the older installed SmartStock Java runtime. No live app/profile was updated.

Artifact: `target/release-windows-1.1.47/smartstock-windows-1.1.47.zip`

- Size: 167,098,504 bytes.
- SHA-256: `f03e37540f8666660dbf34337be936ac7b598d41ec7598f5d93dcfc1769db49b`.
- No installer was created; the selected first-install download is unchanged.
- AI models are separate; the migration bridge and checksum accompany the local
  release output.

Published on 18 October 2026 after the protected publisher fully downloaded the
hosted object and verified its exact size and SHA-256. Release metadata uses
`updates/windows/1.1.47/f03e37540f8666660dbf34337be936ac7b598d41ec7598f5d93dcfc1769db49b/smartstock-windows-1.1.47.zip`.

Update both store servers before relying on round-trip exports of remapped
products, then update registers. Live failed-event replay, register behavior,
physical printing/cash drawers/NFC and cross-store return restocking remain
deployment acceptance checks. Genuine SKU/barcode conflicts and incompatible
one-time additive catalog bootstrap records still require review; see
[catalog identity](cross-store-catalog-identity.md).
