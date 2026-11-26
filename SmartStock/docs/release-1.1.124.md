# SmartStock Windows 1.1.124 — 2026-11-17

Build 101124 fixes SmartStudio's credential allowlist for shared design selection,
shared fonts and processing progress. Update the store server first; SmartStudio
does not need rebuilding for this fix. Sales and administration routes stay denied.

Windows update-only artifact:

- Path: `D:/SmartStock-release-builds/1.1.124/smartstock-windows-1.1.124.zip`
- Bytes: 169330550
- SHA-256: `ceb661486b8aceee30ab29b0e1974c738e242f6cd2caf175ed81b29a91fe4b25`

Built with the standard Windows packaging script. To isolate this hotfix from
unrelated working-tree changes, the staged JAR retains the exact published
1.1.123 `Receipt/OverseasOrderDocuments.class` and
`services/OverseasOrderService.class` bytes. The final ZIP contains that staged
JAR. All other JAR entries were compared against published 1.1.123: only
`services/StudioDeviceEnrollmentService.class` and the three Maven/manifest
version entries differ. The overseas source changes remain untouched. Release
evidence and the staged JAR are outside Git in the release directory.

Validation: full Maven package/test gate, 1539 tests, zero failures/errors,
189 optional skips; repository security and whitespace checks passed. Final
model-free ZIP verification and exact staged-JAR route checks passed. The exact
ZIP applied over isolated 1.1.123 with service/relaunch disabled. Installed JAR
identity, previous-version backup, launcher refresh, model exclusion and persistent
model/profile sentinels passed. The native installed launcher reached the welcome
window using an isolated home. The first acceptance attempt loaded dependencies
from the application being replaced and failed with a file lock; rerunning the
updater from the external baseline JAR succeeded.

No model changes, first-install publication, live installation, store service
restart or live database migration is included. Live SmartStudio workspace/server
acceptance, Windows service behavior, physical printer/drawer/NFC checks and macOS
packaging remain outstanding.

Published through the standard protected publisher. The hosted model catalogue
readiness check passed; the complete uploaded ZIP was downloaded and its size
and SHA-256 matched before metadata publication. Independent metadata read-back
confirmed version 1.1.124, build 101124, Windows platform, published=true, bucket,
immutable content-addressed object path, size and SHA-256. Evidence is saved in
`D:/SmartStock-release-builds/1.1.124/published-1.1.124-metadata.json`.
