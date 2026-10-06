# SmartStock 1.0.240 Windows update

Update-only release prepared from frozen source in `target/release-source-1.0.240`, with a SHA-256 source manifest. No full installer or macOS artifact was built.

## Included

- A standalone installation window showing the target version and current update step after the desktop closes.
- Measured per-file progress during Java ZIP extraction and file copying; animated progress during operations without measurable totals.
- Readable update errors, recovery details and the updater log location.
- Current application changes, including the published 1.0.239 register connection fixes.

## Exact artifact

- File: `smartstock-windows-1.0.240.zip`
- Size: 1,114,066,660 bytes
- SHA-256: `26b09a1dc133b6e1bc1da1b3f1bcb8a831bb97fcede0d6434884bf40111e8edf`
- Build: 100240

## Validation

- Frozen Maven package suite: 812 tests, zero failures or errors, 43 optional skips. An initial incomplete source snapshot failed; the missing development configuration and website source inputs were added before the successful build.
- Frozen repository security check and working-tree whitespace check passed.
- The exact ZIP's application JAR ran the standalone updater window using the installed application's bundled Java runtime, with an isolated user home. The window was visible, closing was disabled, and the Swing event thread remained responsive during extraction, backup and installation.
- A disposable copy of the installed Windows application was updated using the packaged updater's extraction, backup and application-replacement methods. Every payload file matched the archive, and both native launcher configurations referenced 1.0.240.
- The updated fixture launched through its native executable and bundled runtime in client mode. Startup diagnostics confirmed the Welcome screen was visible. Fixture processes were stopped; local verification evidence remains under `target/release-verify-1.0.240`.

## Deployment checks

The installed production application and services were not replaced or stopped. Windows elevation, complete shutdown/service/relaunch orchestration, live databases, paired registers, printers, drawers and NFC remain separate deployment checks. No macOS installation was verified. Update store servers before registers.

Published on October 4, 2026 after the complete stored ZIP was downloaded and its size and SHA-256 matched. Independent release-feed readback confirmed Windows version 1.0.240, build 100240, published status, exact size and SHA-256. Local publication evidence is retained in `target/release-windows-1.0.240`. The full installer selection is unchanged.
