# SmartStock 1.1.54 Windows update

Published 19 October 2026. Hosted artifact size, SHA-256 and release metadata verified.

Build 101054. Adds private organization submission queues, first-use access
codes, restricted resets, stable copyable links, and protected review/print
claims. Completed/rejected submissions and print history require ADMIN.

Update ZIP: `target/release-windows-1.1.54/smartstock-windows-1.1.54.zip`.
Size: 167057162 bytes.
SHA-256: `6d32d335ecce698d98f0832d24c0cd19ba6498c391db2b3c1ada658b6eeb6caa`.
Migration bridge SHA-256:
`6289a1ecb59f079468106fca5bd9a33c7ab8fe909bd3c8cec33ad432459a2dc1`.

Validation: 1134 tests in the packaging gate (90 skipped), zero failures/errors.
The preceding isolated PostgreSQL run enabled all nine queue database tests;
that full gate passed with 81 skipped. Security and whitespace checks passed.
The standard Windows update packager, exact-update isolated native-launch
check, model-free validation and hosted model catalogue check passed.
No SmartStock first-install installer or macOS artifact was built.

Storefront gateway deployed with the protected registered-origin configuration:
version `b304fc17-569c-4910-a01c-90b24a25e09d`. Public page/no-store headers and
unknown-instance isolation passed at `https://www.deckers.gy/shop/template-submit`.
The source-only config intentionally has no production identity information.

Publication/download and independent metadata evidence is in
`target/release-1.1.54-*`. Upgrade the local store server before SmartStudio
0.1.49. The queue remains disabled until its template is explicitly enabled.
Production links must be generated in Studio; the bare page is not an
organization invitation.

Publication does not install or restart the running store. Live database
migration, installed LAN sessions/permissions, mobile camera use, Windows
services, physical printer/drawer/NFC, and installed upgrade/rollback remain
separate acceptance checks.
