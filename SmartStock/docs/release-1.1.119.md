# SmartStock Windows 1.1.119 — 2026-11-15

Cumulative Windows quick update from the shared working tree. Includes product
image coverage and durable square, portrait, story and landscape ad drafts.
Images generated in another chat can be imported and saved as pending drafts on
the local store server, reviewed from authorized registers, and explicitly
approved or rejected. Revision checks prevent stale review and replacement.
No image-generation API integration or API billing is introduced.

Uses the current catalog merge, orders, inventory, account/receipt, embedded web
and server source changes. Separate Cloudflare workers, first-install selections,
and macOS artifacts are not deployed by this Windows app update.

Validation: SmartStock full suite with disposable ad-draft PostgreSQL tests,
1497 tests, zero failures/errors, 165 optional skips. Security and whitespace
checks passed. Packaging skipped repeating this completed test gate. Exact ZIP
applied over an isolated 1.1.118 app: application JAR identity, cumulative classes
and migration, launcher configuration, runtime/profile preservation, model
exclusion and installed native startup passed. The initial seven-second launch
probe was too short; the retry passed with a 45-second deadline. Hosted model
catalogue readiness verified; no changed model weights are part of this release.

Artifact: D:/SmartStock-release-builds/1.1.119/smartstock-windows-1.1.119.zip
Size: 169209712 bytes
SHA-256: 1bd54705a7ee6390a9aa7239c5c983e8c0a7839038c8bac34602d10a8c6a4a30
Build: 101119

Update the server before registers. Live multi-register/database/service checks,
physical printers, drawers, NFC, and macOS acceptance remain separate. Running
store installations and services were not updated or restarted.

Published after full hosted download, size and SHA-256 verification. Independent release metadata read-back matched version, build, immutable object path, bucket, size, hash and published=true. Existing first-install selection is unchanged.
