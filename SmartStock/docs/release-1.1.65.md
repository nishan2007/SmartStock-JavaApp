# SmartStock 1.1.65 dashboard shortcut release

Windows build 101065, 21 October 2026 (America/Guyana).

Adds Overseas Orders to the main dashboard Orders section and the Orders
Dashboard header. The existing top-menu entry and server authorization remain.
Includes cumulative application changes and the 1.1.64 sale-search fixes.

Validation: Maven package passed, 1179 tests, 96 skipped, zero failures/errors.
Security and whitespace checks passed. Model-free archive and hosted model
catalogue verification passed. The exact extracted installer passed isolated
native startup, model-free checks, and matching application JAR identity.
The packaged MainMenu contains overseasOrdersButton. All 862 source inputs
matched the packaging snapshot before publication.

The in-app update is published and independently verified against its exact
size, SHA-256, version, build and object path. The all-in-one installer is also published; its
full remote download verification are recorded in the release publish log.

Update: 167279135 bytes
SHA-256: bab1e378b2c5300d83ab03b0ea4a26715caf5ebad5108494a1b1af32e4749a4e
Installer: 580342484 bytes
SHA-256: 16884f41c9e5edcbf4e90200c61ee548ab372d987921aeaa3dd6d58471c880a5
Migration bridge: 3318 bytes
SHA-256: 32562304de834fac69645523c77ddb9b33016ab5bec71548016742bc75c2554f

Evidence: target/release-windows-1.1.65/package.log, verification.log,
acceptance.log, publish.log, published-release.json, source-snapshot.json,
and artifacts.json. Existing uncommitted work was preserved; nothing staged.

Publication does not update or restart running stores. Update the store server
first, then registers. Installed upgrade/rollback, live service/database and
physical printer/drawer/NFC verification remain separate. No macOS artifact or
AI model weights were built or published. No site redeployment was needed.

