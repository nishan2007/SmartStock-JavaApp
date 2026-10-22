# SmartStock 1.1.56 Windows release

Build 101056, 19 October 2026. The update is published and its independently read
metadata matches the fully downloaded, verified artifact. The first-install EXE
was also fully downloaded and verified before the public Windows selection changed.
Public installer: https://downloads.deckers.gy/download/windows

Scope: default browser Image Studio to GPU preferred with CPU fallback, carrying
forward all current cumulative source changes through 1.1.55. The companion
SmartStudio 0.1.53 release contains passport printer and responsiveness fixes.
The PICS gateway was deployed with home-screen activation, in-app QR scanning,
PNG icons (180/192/512) and standalone manifest metadata. Live HTTPS checks
returned 200 for the uploader, QR decoder and manifest. Gateway version:
69c89498-ad02-4f9f-b91b-09b63756a39a.

Cross-chat audit: reviewed the related SmartStock/Studio chat histories, current
working-tree changes, prior hosted release metadata, and both older worktrees.
Prior template/labels/banners/large-artwork/clipboard, custom-item pricing,
call-status, photo queue/background toggle, customer merge, deposit override,
change-basket and sync/transport changes are already in the cumulative source
and retained. The older price-tag worktree's quantity/variant/color/brand changes
are superseded by the current implementation and its matching regression test;
the photo-release worktree is clean. No historical patch was blindly reapplied.
Runtime image recovery and store configuration tasks remain separate operational
work, not application-release artifacts.

Validation: 1146 SmartStock tests, 91 skipped, zero failures/errors; 182 Studio
tests, none skipped or failed; 62 gateway tests. Security and whitespace checks
passed. The model-free update and unchanged hosted model catalogue passed the
standard verifier. Exact update startup passed in an isolated user profile.
The exact extracted all-in-one installer also passed application JAR identity,
model-free file checks and isolated native startup. A source snapshot records
959 release inputs; all were unchanged when checked after packaging.

SmartStock/target/release-windows-1.1.56/smartstock-windows-1.1.56.zip
Size: 167068281 bytes
SHA-256: 3f01f73cf295e03af4b8ab8b5d2955673c61dacb7a63929dd8477c4b89766398

SmartStock/target/release-windows-1.1.56/smartstock-windows-setup-1.1.56.exe
Size: 580132113 bytes
SHA-256: 4cdd33a0529b8026c2bb91652cae06f79d4a0c3096ab8214e0e6081054b3b30c

SmartStock/target/release-windows-1.1.56/smartstock-ai-model-migration.jar
Size: 3318 bytes
SHA-256: d814bbf0459cbf00667963658f8fce579067f5bb0e78b364309c240021caf1ba

Evidence is under target/release-1.1.56-*: package.log, verification.log,
acceptance.log, installer-acceptance.log, publish.log, metadata.json,
installer-publish.log, public-download.log, artifacts.json, gateway-tests.log and gateway-deploy.log.

No running store was installed, updated, restarted or reconfigured. No model
weights changed or were published; weights remain outside app/rollback folders.
Live database migration, store auth/permissions, Windows services, physical
Canon/Magicard printing, drawer/NFC, phone-camera pairing/upload and installed
upgrade/rollback remain separate acceptance checks. No macOS artifact was built.
Update the store server first, then registers and SmartStudio.
