# SmartStock 1.1.75 Windows quick update

Published 22 October 2026. Build 101075. Includes all saved application changes
captured from the shared workspace, including the latest mobile item web and
custom-order workflow changes. Source was frozen in an isolated build folder to
avoid concurrent chats modifying Maven output. The accumulated SmartStock,
SmartStudio, website and Worker sources are included in the release commit.
SmartStudio 0.1.66 was already published and has no changes since the preceding
release snapshot. This release publishes an updater ZIP only; the first-install
selection remains unchanged.

Validation: 1,250 SmartStock tests, zero remaining failures/errors, 112 skipped;
236 SmartStudio tests passed; 62 storefront Worker tests passed. The isolated
export initially changed generated website line endings; exact original bytes
were restored and both storefront bundle tests passed on recheck. Repository
security checks and the working-tree whitespace check passed. The staged diff
retains existing blank-at-EOF warnings and whitespace in generated/vendor/font
files; the project whitespace check excluding those and allowing blank EOF passed.

The exact ZIP/JAR identity, model-free contents, retained runtime/notices, hosted
model catalogue and isolated native launcher startup passed. The publisher
downloaded the entire hosted ZIP and verified its size and SHA-256 before creating
release metadata. Published metadata was independently checked against these bytes:

- ZIP: `smartstock-windows-1.1.75.zip`, 167494880 bytes.
- SHA-256: `0b4d37d27e1af09e5a884f8b38aa999452d4d4bcb4936de7ac32ee292ae9347d`.
- Object: `updates/windows/1.1.75/0b4d37d27e1af09e5a884f8b38aa999452d4d4bcb4936de7ac32ee292ae9347d/smartstock-windows-1.1.75.zip`.

Supabase: applied seven pending canonical migration entries, covering Studio
submission permissions, employee-web permissions, overseas permissions and loyalty
refund metadata. Local-table migrations now have existence guards and retain their
local effects without creating POS tables in Supabase. Cloud readiness, all eight
new mobile permissions, refund metadata and service-only refund RPC access were
verified. All migration checksums match, apart from the explicitly supported
historical built-in-permission checksum. The exact packaged cloud resource
fingerprint matches the deployed contract. No Edge Functions were pending.
The advisor still reports existing helper-function/Auth warnings and intentional
service-only tables without client RLS policies.

No live store installation or service was updated or restarted. Physical printer,
cash drawer, NFC, live local-database upgrade, installed-app upgrade/rollback and
real-phone checks remain outstanding. macOS was not built or published.

Evidence is under `SmartStock/target/release-1.1.75-*` and
`SmartStock/target/release-windows-1.1.75` and is excluded from Git.
