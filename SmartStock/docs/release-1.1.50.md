# SmartStock 1.1.50 (build 101050)

Windows update ZIP for optional background removal on passport photo queue
uploads and clipboard images/files in custom-order attachments (Paste / Ctrl+V,
including WhatsApp Web images). The live `pic.deckers.gy` uploader was checked
and already contains the background-removal checkbox; no gateway deployment
was needed. Existing SmartStudio installations consume the prepared queue photos.

Also includes customer phone ownership enforcement and administrator preview/
merge of matching customer name/phone duplicates. Installing this release does
not automatically merge accounts; use Customer Accounts → Merge Duplicates.

The standard Windows update-only packager completed Maven tests: 1,115 tests,
zero failures/errors, 81 skipped. The photo queue tests passed (10 tests), and
custom-order file/clipboard tests passed (6 tests, one environment-dependent
skip). Gateway tests passed (53 tests). Repository security and whitespace checks
passed. Customer-merge and phone-ownership PostgreSQL tests were also run against
a disposable database during implementation; they are opt-in in ordinary builds.

The exact ZIP passed the model-free layout verifier and hosted model catalogue
readiness check. It was extracted into an isolated native-launcher layout using
a fresh profile, and the welcome window opened successfully. This is launch
acceptance, not a live server upgrade or production database verification.

Artifact: `smartstock-windows-1.1.50.zip`

Bytes: `167031561`

SHA-256: `a489c45f61cd0334583b28a54eb82c42ae6f66bb039bcecf752bc2655362e177`

No installer or model was built or selected for publication. macOS packaging,
live Windows service/database upgrade, interactive clipboard use in installed
WhatsApp, physical passport printing, drawers, and NFC remain separate checks.
Update the store server first, then registers. Publication does not install or
restart running applications.

Published on 18 October 2026 after a complete hosted download verified the exact
size and SHA-256 above. A separate authenticated metadata GET confirmed version
1.1.50, build 101050, platform Windows, size, SHA-256, immutable object path, and
published state. Verification evidence and the exact artifact are archived under
`D:/SmartStockReleases/release-windows-1.1.50/a489c45f61cd0334583b28a54eb82c42ae6f66bb039bcecf752bc2655362e177/`.
