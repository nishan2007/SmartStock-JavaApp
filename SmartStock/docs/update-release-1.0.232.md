# SmartStock Windows update 1.0.232

Prepared on 2026-10-01, build 100232. Update-only release superseding 1.0.231.

## Correction

A locked desktop cloudflared executable caused the old updater and rollback to delete libraries partially. The corrected updater stages a complete application directory before swapping it, preserves the old installation when the swap cannot proceed, updates launcher configuration before activation, and stops bundled tunnels from both desktop and service paths. Rollback errors are retained without masking the original failure.

## Artifact

- ZIP: `target/release-windows-1.0.232/smartstock-windows-1.0.232.zip`
- Size: 296,613,800 bytes
- SHA-256: `a98c2cfccdc06ca5c29565276f24331c4de0182bb40a69bff8395f657095d9ae`

## Verification

- Existing Windows packaging script completed Maven package and tests: 764 tests, no failures/errors, 33 environment-dependent skips. All 20 updater tests passed.
- Repository security check and `git diff --check` passed.
- A probe compiled against the actual final ZIP JAR and run with the installed Java runtime verified Windows file-lock recovery, complete replacement, rollback, launcher configuration, and Gson/JNA loading in isolated fixtures.
- The final ZIP JAR hash matched the Maven build output.
- The production installation was not upgraded during artifact verification. A visible desktop upgrade and register reconnection remain separate acceptance checks.

## Publication

Published successfully. The stored ZIP was downloaded and its size and SHA-256 matched before release metadata was published. A production feed read confirmed 1.0.232 / build 100232 is the latest published Windows release with the expected size and hash.

## Compatibility

The already-repaired local installation has the updater hotfix needed to apply this update safely. Publishing a new ZIP does not replace the updater used by an older installation to perform its first upgrade. Other machines affected by missing libraries or locked-executable failures need their installed updater repaired first. This release does not change the installer portal selection or publish a Mac artifact.
