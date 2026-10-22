# SmartStudio 0.1.51 Windows update

Build 1051, published 19 October 2026, with SmartStock 1.1.55 client libraries.

Adds 100 MiB PNG/JPEG banner sources and 5 GiB local ZIP64 templates, preserving
original bytes with disk-backed asset storage, bounded previews and streamed
save/open. Undo/copy/paste retain asset references. PDF output uses disk-backed
content streams, vector gradients, original photo resolution and physical page
sizes. The 100 MP decoded-image limit remains separate. Server-shared template
and AI processing limits are unchanged. See banners.md for disk/cache guidance.

Installer: SmartStudio-setup-0.1.51.exe
Size: 189747254 bytes
SHA-256: cd9ac9d130ed8a22649912f49d74ecb767b4bb9047398651dc822259f6b56abc

Portable ZIP: SmartStudio-0.1.51-windows-20261019-150444.zip
Size: 192132543 bytes
SHA-256: 89084aa49e134ec73d729f963dd748747a85f35a523ea07382b8ef161dd8e942

All 177 Studio tests passed during packaging. The preceding SmartStock suite
passed (1146 tests; 91 skipped, including the opt-in large-file proof). The
separate opt-in proof saved and reopened a real 5348560698-byte ZIP64 template
using a 256 MiB heap, with 51 x 100 MiB binary asset fixtures and no Base64 asset
payloads in the opened project. This verifies large-file storage, not physical
printing of 5 GiB of artwork.

The exact extracted installer passed JAR/dependency identity, native startup,
model-free loose-file/JAR checks and a bundled-runtime feature acceptance with
512 MiB heap. That check exercised exact 100 MiB PNG file import, bounded
preview, streamed template save/reopen, source SHA-256 identity and actual-size
PDF export. Only resolved manifest dependencies are packaged, avoiding stale
PDFBox/FontBox versions. The hosted verified model catalogue, repository
security and whitespace checks passed.

Publication evidence: target/release-0.1.51-publish.log, metadata.json,
public-selection.log and public-download.log. The hosted artifact and public download were both fully downloaded and
verified against the exact installer size and SHA-256. Independent release
metadata matched version, build, size, hash and object path. The public
installer selection was updated after verification. Public installer URL:
https://downloads.deckers.gy/download/smartstudio/windows

No running installation was updated or restarted. Installed upgrade/rollback,
store sessions, macOS and physical large-format printer/RIP checks remain
separate acceptance steps. No macOS artifact was built.
