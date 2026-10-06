# SmartStock 1.0.241 Windows update

Corrects background server startup defects identified in the Roshehall 1.0.240 update report. A restart request previously counted as success even when the server did not become available. The command launcher depended on Java on PATH, and the persistent shortcut could point to a removed JAR.

## Changes

- Before stopping a Windows server, repair and verify its scheduled task and shortcut. Reject an existing task registered under a different Windows account before replacing its action.
- Use a stable command launcher with an absolute Java runtime path, explicit original user home and selected environment. Resolve the installed JAR at launch time, capture server stdout/stderr and record exit codes in `sync-service.log`.
- Enable task retries and remove the execution time limit while retaining the existing interactive-user supervisor and DPAPI identity. This release does not migrate servers to a Windows service or change their account.
- Require the expected application version, local schema readiness, certificate-verified loopback HTTPS health and matching UDP discovery before update completion. Startup verification waits up to 90 seconds, with bounded network probes.
- Export only the existing public LAN certificate for the JDK-only staged updater. No private key, pairing code or server credential is copied to a register.
- Skip local server operations for register-only manifests. Older manifests with an existing service copy still receive restart verification.
- Retain the new installation and backup if server startup fails after initialization; an automatic downgrade could be unsafe after database migrations. Show an update failure and the recovery outcome instead of claiming success.

## Exact artifact

- File: `smartstock-windows-1.0.241.zip`
- Size: 1,114,075,614 bytes
- SHA-256: `0b8fae1e1496785a85f7dc4ff72f0c4ebd9617d492b597fb568c694c747dabe1`
- Build: 100241
- Frozen source and SHA-256 source manifest: `target/release-source-1.0.241`

## Validation

- Frozen Maven package suite: 820 tests, zero failures or errors, 43 optional skips. An outdated assertion expecting the previous direct-Java task action was updated before the successful build.
- Repository security and whitespace checks passed. The changed standalone installer script parsed successfully.
- Regression coverage exercised the generated PowerShell repair script with mocked Task Scheduler commands and real temporary shortcuts: missing task creation, existing task update and stale shortcut repair. No production task was changed.
- A real command launcher ran with Java absent from PATH, used the explicit runtime and home, captured stderr and propagated a nonzero startup exit code.
- Real isolated HTTPS and UDP listeners verified pinned certificate trust, matching discovery, expected version and local schema readiness. Wrong identity, unhealthy responses and startup timeout were rejected.
- The exact stored ZIP's staged JAR applied the payload to a disposable installed-app copy using the installed application's bundled runtime. Every payload file matched, both native launcher configurations referenced 1.0.241, and the installation window remained responsive.
- The updated fixture launched through its native executable and bundled runtime in client mode; diagnostics confirmed the Welcome screen was visible. Fixture processes were stopped.

## Deployment checks

Read-only inspection found this computer's current SmartStock server task running and TCP 8443 listening. Its production installation, task and database were not modified. Actual elevated task registration, complete production service restart, cross-device LAN reachability, printers, cash drawers, NFC and macOS remain separate deployment checks. Update servers before registers.

The complete stored ZIP was downloaded and its size and SHA-256 matched before publication. Independent release-feed readback verified version 1.0.241, build 100241, published status and the exact artifact size and hash. Publication evidence is retained locally in `target/release-windows-1.0.241`. No full installer was replaced.
