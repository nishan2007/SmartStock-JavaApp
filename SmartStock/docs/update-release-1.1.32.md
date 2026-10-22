# SmartStock 1.1.32 Windows

Build 101032 packages automatic shirt-designer material/preset discovery,
including vinyl, transfer paper, embroidery and future active material options.
Repeated text lines and logos retain their own fixed-price/per-line charges,
preview dimensions, colors and order-import descriptions. The existing current
garment model assets are included without editing the model implementation.

Validation: packaging Maven gate ran 1,018 tests, 60 skipped, no failures/errors;
security and whitespace checks passed. Website production build, 38 website
tests, legacy shirt browser acceptance and new automatic-material desktop/mobile
browser acceptance passed. The opt-in database integration test was skipped.
Exact installer extraction, installer/update JAR hash equality, model-free
contents and isolated register-profile launcher startup passed.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 164906351 | bfe0809e186703b0b6b448f9bb073269189d4e7a9bd425da219558bbad6bfe79 |
| All-in-one installer | 577994966 | 8fbecaa78b9691adc57cb1fc51ae1263a6cf69f3dc263bc9cdf73774b4b578b0 |

The update upload was fully downloaded and verified before release metadata
publication. Metadata readback matched version 1.1.32, build 101032, published
status, content-addressed object path, exact byte size and SHA-256.
The installer upload was also fully downloaded and verified against its exact
size and SHA-256 before public selection. Publication succeeded at
https://downloads.deckers.gy/download/windows.

No live application was installed or restarted. Live database/service acceptance,
update/rollback and printer, drawer, NFC and physical shirt-production checks
remain outstanding. macOS packaging requires a Mac. SmartStudio was not packaged
or published as part of this release.
