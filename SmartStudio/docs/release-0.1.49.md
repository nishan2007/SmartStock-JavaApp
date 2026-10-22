# SmartStudio 0.1.49 Windows release

Published 19 October 2026. Hosted artifact size, SHA-256 and release metadata verified.

Build 1049, using SmartStock 1.1.54 client libraries. Adds organization link
creation/copy/rotation, permission-protected access-code resets, persistent
submission queue review/rejection and admin recovery. Queued details/photos
load into the current design. Print claims and atomic record creation prevent
silent double processing. Rotating/revoking a link preserves its access code;
only the reset permission can clear that credential. Completed history is
admin-only.

Installer: `SmartStudio-setup-0.1.49.exe`.
Size: 189701370 bytes.
SHA-256: `27a49805d84216702d4a74d023f2bf43079999f5248850ccf5dd76c19b82f851`.
Portable ZIP: `SmartStudio-0.1.49-windows-20261019-123942.zip`.
Size: 192086464 bytes.
SHA-256: `42036666f0e4eb911ddf3454a560d5190def284c760bd4a4bb48c562c622f10d`.

All 166 Studio tests passed, as did the gateway's 59 tests and repository
security/whitespace checks. Standard Windows packaging and the exact extracted
installer passed native startup/JAR identity checks. Loose files and JARs were
checked for model weights; none were present. Hosted model catalogue passed.
Publication and public-download evidence is in `target/release-0.1.49-*`.

Public installer: https://downloads.deckers.gy/download/smartstudio/windows
Upgrade the SmartStock server to 1.1.54 first. The gateway form is deployed,
but per-template organization links require the updated server and enabled
record saving. No running installation was upgraded or restarted. Live mobile,
LAN/admin permission, database migration, physical printing, and installed
upgrade/rollback checks remain outstanding. No macOS build was produced.
