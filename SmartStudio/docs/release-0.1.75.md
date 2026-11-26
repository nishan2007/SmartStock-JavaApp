# SmartStudio 0.1.75 Windows quick update

Build 1075, 24 October 2026. SmartStock client dependency 1.1.80.

Layout offers Portrait/Landscape template orientation. Artwork, all publication pages, masters and guides turn together without stretching; printer paper settings remain independent. Picture format offers left/right 90°, 180° and custom degree rotation. Rotation persists with templates and supports undo/redo. Badge photo/artwork rendering also supports rotation. The Windows packager now escapes the Explorer icon-refresh constant correctly in its generated Inno script.

Update-only artifact: `SmartStudio-update-0.1.75.exe`, **171,291,738 bytes**.
SHA-256: `b07d4c922943675c7ce32436849b3d1b86af7eea40e8b871c093e1e253682530`.

Validation: package build ran 289 tests, no failures/errors, one skipped. Security, whitespace and hosted model catalogue readiness passed. Exact extracted JAR identities, required classes/resources, model exclusion, inference runtime and notices passed. Native launcher and ribbon rendering passed in an isolated profile. Isolated 0.1.74 → 0.1.75 upgrade replaced application files, preserved Java and passed native startup. Rotation checks cover geometry, badge rendered pixels, archive persistence, undo/redo and ribbon bounds at laptop/monitor logical scaling budgets.

No full installer, first-install portal selection, live installation, server deployment or store database change. Native Windows scaling, physical printing, live paired authentication, hardware, rollback and macOS packaging remain separate checks. Evidence: `SmartStudio/target/release-0.1.75-*`, `acceptance-0.1.75-update`, `update-install-0.1.75` and `picture-rotation-ribbon.png`. Protected publisher credentials remain outside Git.

Published successfully. The complete hosted executable matched the exact size and SHA-256 before release metadata was written. Independent metadata verification matched version, build, platform, published status, object path, size and hash.
