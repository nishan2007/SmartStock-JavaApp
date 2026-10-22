# SmartStock 1.1.67 Windows update

Build 101067. Account-charge receipts print the current account ledger balance
including the already-posted charge. Charges with saved representative
authorization also receive a blank Signature line, without collector/date fields.
The section appears in thermal/text and Letter rendering and reprints.
Reprints retrieve the current ledger balance at the time they are generated.
Upgrade the server before registers to provide the balance payload; older servers
show Unavailable rather than an invented zero balance.

Update ZIP: target/release-windows-1.1.67/smartstock-windows-1.1.67.zip
Size: 167301601 bytes
SHA-256: 94e21cceee491aec14f0521bc25c3e78dc3b668d7fa0809b47768325a040dc77

Full packaging gate: 1180 tests, 96 skipped, no failures/errors. Security and
whitespace checks passed. Exact-update isolated native startup, model-free
package and hosted model catalogue validation passed.
Evidence: target/release-1.1.67-*.log.
No first-install installer or macOS artifact was produced. Running installations
were not upgraded. Live database/LAN, installed upgrade/rollback and physical
receipt printer/drawer/NFC checks remain separate acceptance steps.

Published 21 October 2026. Full hosted download size and SHA-256 matched;
published version/build/object metadata independently verified.
Migration bridge SHA-256: a605206957fdd55c9f9d212dfda993b89917624710bbc121ee96de3f4fd59411
