# SmartStudio 0.1.52 Windows update

Build 1052, 19 October 2026, with SmartStock 1.1.55 client libraries.

Removes the old 1,000 mm drag-resize and movement limits in the Banners
workspace. Banner artwork can extend beyond the page for bleed and cropping.
Locked proportions and independent width/height stretching both support large
physical dimensions, up to the existing 1,000,000 mm geometry limit.

All 179 SmartStudio tests, repository security and whitespace checks passed.
The exact installer was extracted and checked for application/dependency JAR
identity, native launcher startup and absence of ONNX weights. Its bundled
runtime passed locked and unlocked 100 ft and 300 ft banner stretching checks.

Installer: SmartStudio-setup-0.1.52.exe
Size: 189747565 bytes
SHA-256: f4c7dcebdc079fc9804655f5671caff64efe8c95d78b1d976ac7225786aab5db

No running installation was updated or restarted. Interactive installed upgrade,
rollback, macOS and physical printer/RIP checks remain outstanding.

Portable ZIP: SmartStudio-0.1.52-windows-20261019-152234.zip
Size: 192132968 bytes
SHA-256: 675327c9cb6e2fda76ebb29352c5ccd28b2f6994dca302156670c6339bf40616

The hosted artifact and public installer were fully downloaded and verified
against the exact size and SHA-256 before/after public selection. Independent
release metadata matched version, build, hash, size and object path. Hosted model
catalogue readiness passed. Publication evidence is in target/release-0.1.52-
publish.log, metadata.json, public-selection.log and public-download.log.
Public download: https://downloads.deckers.gy/download/smartstudio/windows
