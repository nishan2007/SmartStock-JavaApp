# SmartStock 1.1.64 Windows hotfix

Published 21 October 2026 (America/Guyana), build 101064, as an update-only release.

Corrects product searches referencing `is_shirt` and `shirt_designer_config`,
which belong to custom-order items rather than ordinary products. Shirt search
terms remain on the custom-item and custom-variant search paths. Sale search
also falls back to the server for cached misses or missing search indexes and
reports failed requests with a visible Retry action.

Validation: Maven package and tests passed (1179 tests, 96 skipped, zero failures
or errors), repository security and whitespace checks passed. The exact update
passed model-free archive and hosted model catalogue checks, packaged JAR
identity, and isolated native Windows launcher startup using the prior release
runtime. Release source hashes remained unchanged during packaging. No AI model
weights changed or were published.

The publisher fully downloaded the stored object and verified its size and
SHA-256 before publishing release metadata. An independent metadata read matched
version, build, size and SHA-256. The prior first-install selection was retained.

- Artifact: `smartstock-windows-1.1.64.zip`
- Size: 167278698 bytes
- SHA-256: `b7ec20c1feb86809ce0a8d68aaf04844733a877aacbd1321f9bfca9ac762b0cc`
- Object: `windows/1.1.64/b7ec20c1feb86809ce0a8d68aaf04844733a877aacbd1321f9bfca9ac762b0cc/smartstock-windows-1.1.64.zip`

Evidence: `target/release-windows-1.1.64/` contains package, acceptance,
verification and publication logs, source hashes and independent release metadata.

Update POS-SERVER first, then registers. Publication did not install or restart
the live store. Live sale search, inventory refresh, database/service upgrade,
rollback and physical printer/drawer/NFC checks remain outstanding. No macOS
artifact or replacement first-install package was produced.
