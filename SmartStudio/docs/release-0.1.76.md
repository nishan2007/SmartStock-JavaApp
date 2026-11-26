# SmartStudio 0.1.76 Windows quick update

Build 1076, 24 October 2026. SmartStock client dependency 1.1.80.

File backstage precedes Home, with Save As, Rename, file information, local recent files, import/export, printing and close. Home remains default. Shared saves retain the existing permission and verification path. Local copy/rename checks guard conflicts and stale files. The workspace chooser now uses icon cards; Layout/Print Setup/Text/Picture ribbons have labelled groups. Badge Cards uses floating Layers with saved visibility, consistent formatting, size and output controls. Passport Pictures exposes Photo Queue. The update handoff waits for the initiating desktop process to exit before starting setup.

Update-only release: Java runtime and model weights excluded; inference runtime and notices retained. No full installer or first-install selection requested. No live installation, store-server deployment or database change. Paired-store writes, physical printers, native manual scaling/hardware and macOS packaging remain separate checks.

Artifact: `SmartStudio-update-0.1.76.exe`, **172,271,813 bytes**.
SHA-256: `02fe85232309c7c75d1848311f64cdd2181d45ea1dacfeba2a85128601e3af09`.

Validation: isolated Maven package build passed 317 tests, zero failures/errors, one skipped. Security, whitespace and hosted model readiness passed. Standard build output was disturbed during earlier shared-directory builds; the successful release was compiled/tested in `target/release-build-0.1.76` using the repository sources/resources and normal test working directory. Its JAR hash matches the packaged JAR. Exact artifact extraction checked classes/resources, core dependency identity, notices, inference runtime and model/Java exclusion. Native launcher and ribbon rendering passed. Isolated 0.1.75 → 0.1.76 update replaced application files, preserved Java and passed native startup.

Evidence: `SmartStudio/target/release-0.1.76-*`, `release-build-0.1.76`, `acceptance-0.1.76-update`, `update-install-0.1.76`. Protected publisher credentials remain outside Git.

Published successfully. The complete hosted executable matched exact size and SHA-256 before update metadata was written. Independent metadata verification matched version, build, platform, published status, object path, size and hash. No first-install selection or live installation was changed.
