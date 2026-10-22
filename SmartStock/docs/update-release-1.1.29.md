# SmartStock 1.1.29 Windows

Build 101029 fixes custom inventory search. Searches use item names,
descriptions, sizes, colors, brands and barcodes instead of raw JSON. Multiple
words can match parent and variant details, independent of case and spacing.
Matching variants expand automatically; clearing search restores the list.

This is a desktop fix. Update each workstation using custom inventory. A server
on 1.1.28 does not require updating for this search fix alone. Earlier shirt
features remain included and require their existing server/catalogue setup.

Validation: Maven package 1002 tests, 59 skipped, zero failures/errors;
all eight inventory tests pass. Bash security and git diff --check pass. Exact
extracted installer native startup passes in an isolated user home, all 24
updater payloads match, PostgreSQL pinned hash passes, and no model weights are
included. Hosted model catalogue verification passes.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 161770084 | 57c41920081c21e20a17e9d55547bfeb898c7eccf9da1445192f1ea2210d8c14 |
| Installer | 575143104 | e5114f13addbaf97bc1c0e979b80a3acc8150c17aea5297d0d9d5ce27edd71ce |

The updater hosted download matched size and SHA-256 before publication.
Release metadata readback matches version, build, platform, bucket, path, size,
hash and published status.
The complete hosted installer also matched its size and SHA-256 before public
selection. Public download returns HTTP 200 with the 1.1.29 filename and
575143104 bytes.

No live application, database or service was installed or changed. Installed
store search, update/rollback, service/database and physical printer/drawer/NFC
checks remain separate. macOS packaging requires a Mac.

Download: https://downloads.deckers.gy/download/windows
