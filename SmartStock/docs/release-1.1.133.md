# Restore device — 1.1.133

Mobile Management supports restoring a revoked phone without creating a new
approval. Selecting a revoked row changes Revoke phone to Restore device. The
confirmation explains that existing approved browsers regain access to the
device's existing apps and employee permissions still apply.

The server enforces administrator permission, current revision, revoked status,
idempotency and audit recording. Restoration does not change app grants, names
or credentials. No database schema change is required.

Prepared from frozen 1.1.132 plus the restore service, LAN endpoint/client, UI and
focused regression tests. Package and publication verification is retained under
`D:/SmartStock-release-builds/1.1.133`, outside Git. Publication does not install
the update or restart the store server.

## Validation

The full frozen-source suite completed with 1571 tests, zero failures/errors and
201 skipped. After adding the shared Remote Admin mutation classification, the
standard package was rebuilt with focused restore/remote-policy tests: 15 tests,
zero failures/errors, 9 skipped. This includes the real HTTPS/disposable-database
acceptance and four Remote Admin boundary tests. The selected web suite passed
39 tests, and security/whitespace checks passed.

Acceptance checked successful restoration using an existing credential and app
grant, ignored app-edit payloads on restore, stale revision rejection, idempotent
retry without another revision/audit, already-approved rejection, one audit with
the acting employee, and rejection of non-administrators. The Swing preview test
contains the revoked-row button/enabled-state assertions but was skipped in the
headless release environment; real desktop/iPhone acceptance remains separate.

The final JAR/ZIP and exact restore service, LAN server/client, Remote Admin policy
and Mobile Management classes passed content verification. Model-free packaging
and the hosted model catalogue also passed verification. No new models, schema,
Cloudflare routes or physical hardware changes are part of this release.

## Publication verified

Windows 1.1.133/build 101133 was uploaded, downloaded in full, and verified before
release metadata creation. Published metadata was independently checked against
the exact version, build, object path, size and checksum.

- ZIP bytes: 169727599
- ZIP SHA-256: bb02fe1697ae42934ed522b477232139a6dc9db5f2508ad453e21b014e18f0dc
- Packaged JAR SHA-256: 22578665cc20b029d37d102e28df8db36bc949b1d758d3930d5c8760fd2ccbcc

No application was installed or restarted. No first-install package was selected,
and no model publication or gateway deployment occurred. Install the update on
the store server and the workstation used for Mobile Management to use Restore
device. Live installed-app and physical hardware checks remain user acceptance.
