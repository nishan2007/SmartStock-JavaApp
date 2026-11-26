# SmartStudio 0.1.73 Windows quick update

Build 1073, 23 October 2026, using SmartStock client dependency 1.1.80.

Banners → Print now sends actual-size artwork through an installed Windows printer driver. Printer selection and page setup precede a size check and confirmation. Mismatched paper dimensions are rejected; PDF export remains available. Reference pages are excluded and merge records must be cleared.

Artifact: `SmartStudio-update-0.1.73.exe`, **171,283,370 bytes**.
SHA-256: `bf9609a52f8fe7fb7c08fb768497f0ad784466df889f2394ac783e2e66ee02d2`.

Update-only executable for existing installations. Java runtime and model weights excluded; inference runtime and notices retained. No live store installation or first-install portal selection changed.

Validation: Studio test/package build, 281 tests, zero failures/errors, one skipped. Security and whitespace checks passed. Hosted model catalogue readiness passed. Exact extracted Studio/core JAR identities, banner printing class, required resources, model exclusion and runtime/licences checked. Packaged native launcher and ribbon rendering passed. Isolated 0.1.72 → 0.1.73 upgrade replaced the application, preserved Java, and passed native startup.

Physical Canon driver/media setup and printing, live paired authentication, native macOS packaging and rollback remain unverified.

Evidence: `SmartStudio/target/release-0.1.73-*`, `acceptance-0.1.73-update`, and `update-install-0.1.73`. Protected publisher configuration is outside Git.

Published successfully. The complete hosted executable matched the exact size and SHA-256 before release metadata was written. Independent metadata verification matched version, build, platform, published status, object path, size and hash. No first-install portal selection or live installation was changed.
