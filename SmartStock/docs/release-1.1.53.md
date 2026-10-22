# SmartStock 1.1.53 Windows update

Published 19 October 2026.

Build 101053. Server support for opt-in per-template badge print records,
original photos/crops, admin-only history, and current-format reprinting.
Shared template deletion checks management permissions, owner-store access,
and both the selected revision and assignment version.

Artifact: `target/release-windows-1.1.53/smartstock-windows-1.1.53.zip`.
Size: 167037704 bytes.
SHA-256: `b35fc1848a4694cdaa368a6acfe7487e48713cbfd022af6e9ec3ceaf7ee7b11d`.
Migration bridge SHA-256:
`0fe9f078ca72d251015da5b86dc16b26eb9b7eb83eee3d81dd2c17fba7244e75`.

Validation: 1121 tests, 81 skipped, zero failures/errors; security and whitespace
checks passed. Standard update-only Windows packaging, isolated native-launch
acceptance, model-free archive verification and hosted catalogue checks passed.
Publication evidence is in `target/release-1.1.53-publish.log` and the independent
metadata verification log. No first-install SmartStock installer was built.

Update the server before SmartStudio 0.1.48. Publication does not install or
restart live applications. Live PostgreSQL migration/record operations, service,
printer/drawer/NFC hardware, installed upgrade/rollback, and macOS remain
unverified by these checks.
