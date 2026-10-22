# Windows publication — 17 October 2026

Published SmartStudio **0.1.42 / build 1042** with matching SmartStock **1.1.44 / build 101044** client/server packages. Includes multi-object template selection, image recolouring and job-only image-to-ink assignments for Risograph masters. See [selection and recolouring](template-selection-and-recolour.md) and [Risograph workflow](risograph-separations.md).

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| SmartStudio-setup-0.1.42.exe | 225348905 | `c6770ad529ad7f6c0bc46c37bc82ab2abc3c1ea6e1763bcdb5b34e83aaf8a764` |
| smartstock-windows-1.1.44.zip | 167047703 | `d4e162f89f515d1200f270c00f51b423c7fa98e6e7f2fca15d93c736d40c1c53` |
| smartstock-windows-setup-1.1.44.exe | 580126036 | `6a1f43ef6168758024bb94e3ecc55db40609fb388e32adf6efe5c99c78732a61` |
| smartstock-ai-model-migration.jar | 3318 | `0af008a5cbc53867731a2ac33612c2889aed26957e5fd9881caf244159262735` |

Publishers downloaded and verified complete hosted artifacts before changing release metadata or installer selection. Both public installer downloads were then streamed completely and matched exact local sizes and hashes. Both application release rows matched version, build, size, hash, bucket and object path. SmartStock's model-free archive and hosted model catalogue passed verification; no model weights were packaged.

Validation: SmartStock 1,078 tests, 72 skipped, no failures/errors; SmartStudio 140 tests, no failures/errors; repository security and whitespace checks passed. Extracted installers passed isolated native launcher checks. SmartStock installer/updater JARs matched; SmartStudio contains exactly one matching SmartStock client JAR, identical to the tested release. Prior visual checks covered recolouring and picture assignment dialogs.

Publication did not install updates or restart running applications/services. Follow the standard server-before-clients update order. No new store-server API or database migration is required for these local design/print features. Physical printer/Risograph output, NFC hardware, live Windows service operation and skipped database integration checks remain separate acceptance steps. No macOS package was built or published.

Downloads: [SmartStudio Windows](https://downloads.deckers.gy/download/smartstudio/windows), [SmartStock Windows](https://downloads.deckers.gy/download/windows).
