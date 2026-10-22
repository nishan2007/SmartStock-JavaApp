# SmartStock 1.1.26 Windows

Build 101026 shortens custom-order slips by grouping identical items into one
numbered entry with quantity and summed price. Details and instructions appear
once per group on thermal and page-format slips. Different variants,
customizations, instructions or unit prices remain separate. First appearance
order is preserved. Saved unit lines, their identities, order totals and payment
totals are unchanged. Single items keep their existing appearance.

Validation: full Maven packaging suite 979 tests, zero failures/errors, 58
skipped; release-tool tests 21 passed; Bash security and whitespace checks passed.
Regression tests cover five units, equivalent decimal prices, grouping boundaries,
ordering, thermal output and page rendering. The model-free updater and hosted
immutable model catalogue checks passed. Exact installer extraction and native
startup passed with an isolated user home. All 24 updater payloads match the
installer by SHA-256; its bundled PostgreSQL installer matches the pinned hash.
The exact extracted app JAR and bundled runtime pass grouping, summed-price,
instructions-once, thermal output and page rendering checks. The grouped page
slip was visually inspected.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 149201676 | ccab36a21a4cfe5cfda7bf4fbae53e08c45bfa25e638f375ea89ab49bd3c1f6d |
| Installer | 562572758 | 583c5f94dcbe0ff13cb6bd0c1acc44cc7ef83e5b5b661ee9755ba5c5342d754a |
| Migration bridge | 3318 | d29b37435a9848382ed24bdf3e6d5cc01e3fdc6fa7fa06ef22af312c2754875c |

No running store application, live database or Windows service was installed,
updated or restarted. Actual installer/service setup, physical printer output,
drawer/NFC, live LAN, update/rollback, real Publisher conversion and macOS remain
separate verification steps. Update the store server before registers.

Release evidence is saved under `target/release-windows-1.1.26`.

The updater and all-in-one installer are published. Their complete hosted
downloads matched exact local size and SHA-256 before publication. Update
metadata readback matches version, build, platform, bucket, path, size, hash and
published status. Installer manifest readback matches the verified artifact.
Public HEAD returns HTTP 200, the 1.1.26 filename and 562572758 bytes.

Download: https://downloads.deckers.gy/download/windows
