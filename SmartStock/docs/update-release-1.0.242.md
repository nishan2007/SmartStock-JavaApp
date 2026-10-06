# SmartStock 1.0.242 Windows update

Existing stores could fail background-server startup after 1.0.241 because the built-in ADMIN catalog check required MANUAL_CUSTOM_ORDER_ENTRY, but its permission-only migration had no existing-store upgrade hook. The missing LAN certificate was a downstream symptom of database initialization failing.

The guarded built-in permission repair now applies the packaged manual-entry permission migration in the same transaction before validating the current catalog. Existing schema fingerprint and concurrent-metadata guards remain enforced. Updater errors retain the readiness explanation and log location even when an underlying exception contains only a filename.

## Artifact

- Update only; full installer unchanged.
- File: smartstock-windows-1.0.242.zip
- Build: 100242
- Size: 1,114,076,609 bytes
- SHA-256: b9f07709a3373a3a94d0dd17b48cea6a6a25f4384ca7fe8ca20d3c851f16455f
- Frozen source and source manifest: D:/SmartStock-release-1.0.242
- Final package: D:/SmartStock-update-final-1.0.242
- Publication completed: the uploaded artifact was downloaded in full and its exact size and SHA-256 verified before publishing build 100242 to the Windows update feed. Latest update metadata was read back and verified against the same artifact.

## Validation

- Maven package: 822 tests, zero failures/errors, 44 optional skips.
- Separate PostgreSQL regression test: remove the manual-entry permission and ADMIN assignment from a disposable baseline, run existing-store startup, verify restoration and schema readiness. Passed both in JUnit and with the exact packaged JAR.
- Repository security check through Git Bash and git diff --check passed.
- Installed Windows fixture: all ZIP payload files matched after replacement, both native launcher configurations used 1.0.242, and the standalone progress window stayed responsive with closing disabled. The native SmartStock launcher reopened the responsive DECKERS Welcome window using the isolated fixture home and bundled Java runtime.
- Local affected server: read-only diagnosis found only MANUAL_CUSTOM_ORDER_ENTRY missing. Applied the same schema-guarded repair; verified pinned HTTPS health, expected installed version 1.0.241, local schema readiness and matching UDP register discovery afterward.
- Physical register transactions, remote Roshehall recovery, administrator approval during a complete production update, and macOS installed service behavior require separate verification.

Do not downgrade application files automatically after schema initialization. Retain installation backups on startup failure.
