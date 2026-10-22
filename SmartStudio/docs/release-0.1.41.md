# Windows publication — 17 October 2026

Published SmartStudio **0.1.41 / build 1041** and its matching SmartStock **1.1.43 / build 101043** dependency/server release. Includes Risograph paper-master separation for Business Cards and Labels, persistent staff-phone queue pairing, and clearer incorrect-password errors. See the respective release notes and [Risograph workflow](risograph-separations.md).

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| SmartStudio-setup-0.1.41.exe | 225319170 | `5056d86e5c5c29602f962852732581fb5b0209e86f61529af26728ae63dfc7cf` |
| smartstock-windows-1.1.43.zip | 167047142 | `b28e361a6525bded2fcad3ba7e10a4d50538a85c953ed285b7fccba894c04fd4` |
| smartstock-windows-setup-1.1.43.exe | 580121585 | `a94fb64c3de347a594f3d9b54e9a55e99169ea42cb4469757d23da296b3562ab` |
| smartstock-ai-model-migration.jar | 3318 | `02de17cac4362e78ac1f214773ae651415c162e2ef7640a4ac45f96939635349` |

Both installer public downloads were streamed completely and matched their exact local sizes and hashes. Both application release rows were read back and matched version, build, size, hash, bucket and object path. The publishers verified hosted bytes before publication. SmartStock's model-free archive and hosted model catalogue passed verification; no model weights were packaged.

Validation: SmartStock 1,076 tests, 72 skipped, no failures/errors; SmartStudio 124 tests, no skips/failures/errors; photo gateway 51 tests, all passing; repository security and whitespace checks passed. Extracted installers passed isolated native launcher checks. SmartStock installer/updater application JARs matched; SmartStudio contains the exact matching SmartStock dependency.

Publication did not install updates or restart the running store, services or SmartStudio. Update the store server first, then clients. Physical printer/Risograph output, NFC hardware, production database migrations and end-to-end paired-phone upload against an updated live server remain separate acceptance checks. No macOS package was built or published.

Downloads: [SmartStudio Windows](https://downloads.deckers.gy/download/smartstudio/windows), [SmartStock Windows](https://downloads.deckers.gy/download/windows).
