# SmartStock 1.1.68 shared bill book server update

Windows build 101068. In-app update published 21 October 2026.
Adds BILL_BOOKS to the authenticated local publication template store, retaining
store permissions, revision conflicts, archive validation and OneDrive backup.
No database schema change. Includes the 1.1.67 receipt corrections.

Requires SmartStudio 0.1.61 for the shared Bill Books workspace. Update the
server through SmartStock's in-app updater first, then Studio on the importing
computer and other clients. Sign in online on the importing computer and wait
for local template migration to finish. Other clients in the same authorized
store can then download verified archives. Live migration has not yet been
performed or verified by this release task.

Validation: 1181 core tests, zero failures/errors, 96 skipped; all 203 Studio
tests passed, including workspace shared-cache initialization and second-client
migration/download. Security, whitespace, model-free package, hosted model
catalogue, native installer startup and exact JAR identity checks passed. Both
apps contain the same final core JAR; all 996 source inputs matched the snapshot.

Published in-app ZIP: 167301465 bytes
SHA-256: 33d12ef0b4cd74d7de8ea1bec687ab44690e051be63a42e3bff441599aecb6af
Hosted bytes were fully verified before app-release metadata was written.
Metadata independently matched version, build, immutable path, size and hash.
SmartStudio 0.1.61 metadata and its full public installer download were verified.

IMPORTANT: the new all-in-one SmartStock installer is NOT published. The first
multipart attempt failed on a part (HTTP 400); the serial retry uploaded all
parts but completion returned HTTP 400. A protected staged-object read returned
404, confirming that no verified final object exists. No new installer selection
was made. Existing installations can use the already published in-app update.
Do not rebuild these immutable accepted artifacts under this published version;
recover publication using the exact local installer bytes only.

Prepared installer: 580367154 bytes
SHA-256: 647797d83ab7899420641594fc1e2bd7b14f11af98aec2d3de4844c2c1414642

Evidence: target/release-windows-1.1.68 package/retry, security, verification,
acceptance, publish-update, publish-installer/retry and staged-inspection logs;
published-release.json, source-snapshot.json, artifacts.json and
staged-installer-status.json. Packaging recovered a failed tunnel-runtime
network download using the previously verified pinned executable; the unchanged
source had already passed the full tests. The packager reverified its SHA-256.

No live installation/service/database was changed. Actual template migration,
other-computer visibility, upgrade/rollback, source fonts/layout and physical
printing remain separate checks after the updates. No macOS release was made.
