# SmartStock 1.1.1 release preparation

Prepared on Windows for version **1.1.1**, build **101001**. The model upload
hosting support has been deployed with explicit authorization. The application
update has now been published for Windows with explicit authorization; release
metadata readback matches build 101001 and the exact ZIP size/SHA-256. It has
not been installed on the live store server. An isolated
current-user Windows validation installation was completed successfully.

## Verified Windows artifacts

Artifacts are in `SmartStock/target/release-windows-1.1.1`, with SHA-256 sidecars.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| smartstock-windows-1.1.1.zip | 133284240 | c5c15fd29e07e4a63b34e82eae16f3da52de2a03238a82ee88daa6bf51ecad57 |
| smartstock-windows-setup-1.1.1.exe | 546633492 | 7b72a97238b9513d7cc6dabf36a138222483b216471624762e8df2025dea44dc |
| smartstock-ai-model-migration.jar | 3318 | 7d2cdb4a4bea3dcfcd81dbb1fa7bc5c9013da229faec6309fd3327e5b17c162f |

The update archive passed the model-free package verifier: matching application
version, migration/model management support, retained ONNX runtime and notices,
and no model weights. Compared with the existing bundled-model 1.0.249 archive
(1114356270 bytes), this update is 981072030 bytes smaller, approximately **88%**.
This comparison includes other release changes; it is not an installer-size
comparison.

Fast and Best artifacts are separately staged in `SmartStock/target/release-ai-models`
with their catalogue, notices and SHA256SUMS. Fast is 178648008 bytes and Best is
972666916 bytes. Both artifacts and the immutable/live catalogues were published
with authorization and passed full remote download size/SHA-256 verification.
Windows application release metadata now references the verified model-free ZIP.
The matching Windows installer was fully downloaded and verified before the
public Windows selection changed to 1.1.1. Both publications completed successfully.

## Validation

- Standard Windows all-in-one packaging completed successfully.
- SmartStock Maven reports: 864 tests, 47 skipped, zero failures or errors.
- SmartStudio Maven reports against the 1.1.1 client: 13 tests, all passing.
- Model publishing, package guard and hosting Worker tests: 28 passing.
- Repository security check through Git Bash passed.
- Diff whitespace check passed.
- Publishing/packaging script syntax checks passed.
- The exact installer completed successfully in an isolated validation directory;
  the installed payload contains no ONNX weights. Its bundled Java processed a
  generated photo with Fast and Best from the same persistent profile directory.
- The migration helper preserved both full-size legacy models and their originals.
  Initial Best inference in a combined process failed with a native allocation
  error on this machine. Separate bounded-heap processes succeeded for both the
  extracted package and installed runtime; server memory acceptance remains needed.
- Interrupted multipart upload parts now retry before aborting; a disconnect
  regression test passed. The first Best upload failed and did not publish a
  catalogue; the fresh publication succeeded and verified the catalogue readback.
- The release guard passed against the hosted verified immutable catalogue.
- The installed runtime refreshed the catalogue through a real server-signed URL
  and downloaded Fast and Best into an isolated profile, verifying integrity
  before activation. No signing credentials were copied into the candidate.
- Final Windows artifact SHA-256 values were independently recomputed and matched
  their sidecars. The old bundled-model archive was rejected by the package guard.

## Required before distribution

Follow [packaging and publishing](release-packaging-publishing.md) and
[AI model updates](ai-model-updates.md).

1. Hosting support and separate model publication are complete. The signed
   download Worker required no model-specific deployment change. Confirm the
   target store server's signing configuration and administrator UI downloads.
2. Installed Windows runtime processing checks are complete. Validate the live server service:
   shared persistent model paths, legacy migration, photo processing, application
   update and rollback, and administrator enforcement.
3. Build and verify the macOS application, ZIP and DMG on macOS. Older Macs need
   the migration helper before the first model-free update; verify that installed
   flow as documented.
4. Complete applicable live database and printer, drawer, NFC and service checks.
   These are outstanding and are not established by automated tests.
5. Publish only the exact accepted final artifacts, then upgrade the store server
   before registers. Keep SmartStudio's separate release version/channel.

Unrelated workspace changes were preserved. This candidate was built from the
current working tree; no commit or push was performed.
