# SmartStock 1.1.27 Windows

Build 101027 includes final-order-total rounding, mixed-size order sets and
individual editing, held-draft support, shirt setup, website shirt designing and
store quote review. Unit prices remain exact; quantities, add-ons and discounts
are summed before the optional nearest-$20 rounding. Existing finalized orders
are not repriced. The publishing workspace and grouped slips remain included.

Validation: SmartStock Maven package suite 1001 tests, no failures/errors, 59
skipped; SmartStudio Maven tests pass; website build and website/gateway tests
pass; Bash security and whitespace checks pass. Synthetic Edge browser acceptance
passes desktop/mobile editing, upload, grouping, login draft restoration, changed
estimate handling and submission. No real customer records were used.

The exact extracted installer passes native startup with an isolated user home.
All 24 updater payloads match by SHA-256, bundled PostgreSQL matches its pinned
hash and no model weights are included. Hosted immutable model catalogue
verification passes. The exact packaged Java runtime verifies a $30 unit remains
$30, 100 units total $3000, and three units round once from $90 to $100.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 161768959 | f2f7140d6e22e7e2c0d317ce907494c1f9796918954169115946fb7f1e0e28e0 |
| Installer | 575141769 | 07345c65349867f2fb0522d4a05dec8230c056dce54dac305ca26ede19ec2f64 |
| Migration bridge | 3318 | 09849d658512aae6691fe0e1d54412c031c913ca37354ca397635f77895a53b7 |

Production storefront gateway deployed version
`9f54115f-acee-44af-91b0-9653d51ee73b`. Browse-only behavior is preserved;
its formerly implicit default is explicit in the protected deployment config.
The website bundle is served by the updated store server after installation.

No running store application, service or live database was installed or changed.
Live LAN/synchronization, actual installer/service setup, update/rollback, real
private uploads/proofs, quote-to-order acceptance, physical printer/duplex/barcode,
drawer/NFC, Publisher conversion and macOS checks remain outstanding.
Update the store server before registers. Evidence is in the ignored release
target directory.

The updater and installer are published. Complete hosted downloads matched their
local sizes and SHA-256 before publication. Release metadata readback matches
version, build, platform, bucket, path, size, hash and published status. Public
Windows download returns HTTP 200 with the 1.1.27 filename and 575141769 bytes.

Download: https://downloads.deckers.gy/download/windows
