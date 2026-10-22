# SmartStudio 0.1.57 Windows release

Build 1057, SmartStock 1.1.62 client dependency.
Fix photo-field replacement rejecting camera photos above 12 megapixels.
PNG/JPG/JPEG sources up to 20 MiB and 100 megapixels are accepted in the
photo-field chooser. Bounded source subsampling prepares at most 12 megapixels
for the framing editor and record image. Other import and service limits remain.

Installer: SmartStudio-setup-0.1.57.exe
Size: 189868750 bytes
SHA-256: 4f743154699fd15a27139d00e510caebb9b5dd9f9dd19778b29fe7f9f52e1cf0

All 192 Studio tests passed, including a camera JPG above the old limit.
Security, whitespace, exact-installer JAR identity, native startup and model-free
checks passed. Publication evidence: target/release-0.1.57-*.log.
No running installation was upgraded. Physical printing, installed upgrade and
rollback remain separate checks. No macOS build was produced.

Published 21 October 2026. Hosted and public full downloads matched the exact
installer size and SHA-256; release metadata independently verified.
Public installer: https://downloads.deckers.gy/download/smartstudio/windows
