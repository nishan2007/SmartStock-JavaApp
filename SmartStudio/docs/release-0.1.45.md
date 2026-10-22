# SmartStudio 0.1.45 / build 1045

Adds layer copy/paste across pages and compatible templates, multi-layer selection support, and external plain-text/image paste. Canvas and Layers shortcuts use Ctrl+C/Ctrl+V (Cmd on macOS); text editors keep their existing clipboard behavior. Layer snapshots retain geometry, formatting, artwork and linked stories with independent IDs. Badge clipboard snapshots exclude personal photos.

Includes the print-margin fix: new business-card/label layouts default to zero margins; artwork and cut marks at printer-reported margins no longer stop actual-size jobs. Paper-size validation remains. Physical printers may clip at hardware limits.

Uses SmartStock 1.1.51 client libraries. No models changed. Packaging and publication evidence will be recorded below after verification.

Physical printer/Risograph output, interactive installed-app copy/paste, saves, live database, services, drawer and NFC remain separate checks. Publication does not install into a running store. No macOS package is included.

Validation: SmartStudio 153 tests, no failures/errors/skips; SmartStock 1,117 tests, 81 skipped, no failures/errors on the successful rerun. The initial shared-client attempt encountered transient missing-class failures; rerun completed successfully. Repository Bash security check and git diff --check passed. Hosted model catalogue readiness passed.

The portable launcher and the exact extracted installer's native launcher passed startup checks; the login preview was visually inspected. Extracted SmartStudio and SmartStock dependency JAR hashes match the build. All 41 packaged JARs and loose files were checked for ONNX weights; none were included. ONNX Runtime 1.23.2 remains packaged.

Installer: SmartStudio-setup-0.1.45.exe, 189644834 bytes, SHA-256 1e6f20cbcf1e11746c384453846d352f917fbe2bb2113f9bd51f92044e8fe16f.
Portable ZIP: SmartStudio-0.1.45-windows-20261019-085836.zip, 192031483 bytes, SHA-256 3ac0139c8f43fd251614b07aab049c775b8cb7a3e65ba2667d37355d9fccf45d.

Published on 2026-10-19. The publisher downloaded and verified the full hosted installer before creating release metadata. Independent metadata verification confirmed version 0.1.45, build 1045, object path, size and SHA-256. The protected portal configuration was backed up and its Windows SmartStudio selection updated while preserving other selections. Full public download verified against 189644834 bytes and SHA-256 1e6f20cbcf1e11746c384453846d352f917fbe2bb2113f9bd51f92044e8fe16f. Public Windows download: https://downloads.deckers.gy/download/smartstudio/windows

Portal deployment version: c25eba73-0f5a-40a8-92ea-774914d7a957. No running installation changed.
