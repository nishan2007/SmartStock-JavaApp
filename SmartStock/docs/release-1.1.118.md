# SmartStock Windows 1.1.118 — 2026-11-14

The 1.1.117 photo identity guard rejected differing storage providers even when
both stores held the identical image. Rosehall's existing metadata for the two
pending book merges uses OneDrive; Skeldon's uses Supabase. The exact object paths
and SHA-256 hashes agree, but that provider check prevented alias registration.

This release permits provider differences only with an equal, valid 64-character
SHA-256. Exact bucket/path, category and access-level checks still apply. Unknown
or unequal hashes block cross-provider reuse. Local provider configuration,
metadata, file state and the shared approval remain unchanged.

The separate-store PostgreSQL regression now reproduces a Supabase/OneDrive
provider difference, rejects an unverified checksum, accepts the verified hash,
completes the existing merge idempotently, and confirms local provider retention.
Object and content conflicts remain covered. No schema or model changes.

Live Rosehall installation and the remaining merge receipts are separate checks;
they must not be inferred from packaging or isolated tests. Physical printers,
drawers, NFC and macOS checks are outside this change.

Full Maven suite with disposable PostgreSQL enabled: 1488 tests, zero failures/errors, 160 optional skips. Security and whitespace checks passed. Packaging skips a duplicate Maven test run after this completed gate.

Exact updater acceptance over isolated 1.1.117 passed: installed JAR identity, native startup, launcher configuration, runtime/profile preservation and model exclusion. ZIP: 169182776 bytes; SHA-256: 84c01abf559233c95b1b33f0262f838b6b3d12941dc4ece20778043efd6cccc3. No live store installation was performed by this acceptance check.

Published build 101118 after hosted download/size/SHA-256 verification. Independent metadata read-back confirmed version, build, immutable object path, size, hash and published=true. First-install selection is unchanged.
