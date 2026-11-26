# SmartStudio 0.1.78 Windows save compatibility update

Build 1078, 24 October 2026. SmartStock client dependency 1.1.80.

Fixes shared template saves when the store server's semantic content fingerprint
uses a different publication schema. A mismatched semantic fingerprint requires
bounded read-back of the committed revision and exact byte equality with the
uploaded archive. Raw acknowledgement checks remain mandatory; changed metadata,
corrupt bytes, invalid chunks and sign-out prevent successful save confirmation.
Numbering fields, merge records and print settings remain in the saved archive.
The K&V top-number physical alignment issue is not changed by this release.

Artifact: `SmartStudio-update-0.1.78.exe`, 172,273,711 bytes.
SHA-256: `be48756c584a82cac7f1b3b2894b63666482cc75ed70ad2577ed3450bf15f840`.

Validation: Maven package passed 320 tests, zero failures/errors, one skipped.
Repository security and whitespace checks passed. Hosted model catalogue readiness
passed. Exact installer extraction verified application/core JAR identity, runtime
and notices, and exclusion of Java and model weights. Native launcher and ribbon
rendering passed. An isolated 0.1.77 to 0.1.78 upgrade replaced the application,
retained the Java runtime and passed native startup. Installer-created shortcuts
were restored to the live installation after isolated acceptance.

Update-only artifact; no live application/server installation or database change.
Live shared-save acceptance and physical K&V print alignment remain outstanding.
Evidence is under SmartStudio/target/acceptance-0.1.78-update,
update-install-0.1.78 and release-0.1.78-*; protected configuration is outside Git.
Published to the Windows SmartStudio update channel after complete hosted download,
size and SHA-256 verification. Independent release metadata read-back matched version,
build, platform, published status, immutable path, size and SHA-256. The permanent
first-install selection was unchanged.
