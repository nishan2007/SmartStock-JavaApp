# SmartStock Windows 1.1.116 — 2026-11-14

Fixes photo-reference reconciliation when a catalogue URL reaches a store before
its image asset metadata. Registration now locks and uses an existing asset row
in one SQL statement; absent metadata defers registration without changing the
photo URL, inventing metadata, or weakening the foreign key. Existing reference
links remain protected while waiting. Gallery positions include unavailable
entries so subsequent photos keep their original positions. The same handling
covers item, employee/customer, storefront and quotation image references.

The PostgreSQL regression covers missing metadata, existing-link retention,
stable gallery positions, automatic registration after metadata arrival, replay
and continued foreign-key enforcement. This is application code only: no local
or Supabase migration and no model changes are required.

Live Rosehall sync recovery and remaining merge acknowledgements must be checked
after that store installs this release. Windows hardware and macOS checks are
outside this fix's verification.

Validation: full Maven suite with disposable PostgreSQL enabled: 1487 tests, zero failures/errors, 160 optional skips. Repository security and whitespace checks passed. The packaging Maven invocation reuses this completed gate and skips a duplicate test run.

Exact updater acceptance over isolated 1.1.115 passed: installed JAR identity, native welcome-screen startup, launcher configuration, runtime/model-profile preservation and model-free layout. ZIP: 169179086 bytes; SHA-256: 1aeb7f731ec42fc8896a01bf2507f79b77bef10e0174a70d9ca7459ea3b1a2b4. Live stores were not installed by this verification.

Standalone model-migration bridge SHA-256: 0b5c94ea275455c03cdb5ad085c4fe09b633a2f1097c6dc9eec6c2a9f09494f7.

Published build 101116 after hosted download/size/SHA-256 verification. Independent release metadata read-back matched all fields and published=true. This update-only publication leaves the first-install selection unchanged.
