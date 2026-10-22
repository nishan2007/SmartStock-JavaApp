# SmartStock 1.1.52 Windows update

Build: 101052. Staff may save Studio templates within their accessible store
folders; ownership and sharing changes retain the management permission gate.

Artifact: `target/release-windows-1.1.52/smartstock-windows-1.1.52.zip`

- Size: 167032627 bytes.
- SHA-256: `b9d9de5c035890661fa35d32e783ee85de03c564348386da0cdf93caa52a8afa`.
- Migration bridge SHA-256: `6c3aaec8199a0e91fbef66dc6d9600bdf475d907a111152fed0f41779b43459d`.
- Standard Windows packaging with `-UpdateOnly` succeeded.
- Maven tests: 1117 tests, 81 skipped, no failures or errors.
- Repository security check and `git diff --check` passed.
- Update archive verification and hosted model catalogue readiness passed.
- Published the Windows update after a complete hosted download matched its size
  and SHA-256. Release metadata was read back and matched version, build, object
  path, size, SHA-256, and published status.

No first-install selection or macOS release is part of this update. Models remain
outside the application archive. Update the store server before registers.

Installed-app acceptance, a live staff template save, Windows service operation,
database flows, printer/drawer/NFC hardware, model migration, offline processing,
and update/rollback checks remain outstanding. No running store was installed or
restarted during packaging/publication.
