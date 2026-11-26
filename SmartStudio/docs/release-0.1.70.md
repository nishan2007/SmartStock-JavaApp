# SmartStudio 0.1.70 Windows update

Build 1070, published 23 October 2026, using the current SmartStock 1.1.80 client dependency.

Includes direct ribbon layout/print/text/picture controls, inches by default, a movable scrollable Layers panel with persistent visibility and undo, a full-height records editor with field deletion, and corrected font family/size editing with actual font-face choices. Template print setup, fields and records continue to save together.

This is an update-only executable for an existing installation. Application files and dependencies are included; the Java runtime and AI model weights are excluded. The full first-install portal selection is unchanged. Shared-template record saving and upload-based Photo Queue expiry require the updated store server; this release does not install or restart a server or publish the phone-page changes.

Artifact: `SmartStudio-update-0.1.70.exe`, **169,694,220 bytes**.
SHA-256: `26533ea5ad83f7e1817df55a90e60cc5e70e8d8006d539882ee5e71e9a5977c1`.

The publisher downloaded the complete hosted executable and verified its size and SHA-256 before publishing update metadata. Independent metadata verification matched version, build, platform, publication status, object path, size and SHA-256. No public first-install selection was changed.

Validation: core install/test build completed with 1,291 tests (115 skipped), Studio test/package with 272 tests (one skipped), zero failures/errors. Security and whitespace checks passed, hosted model catalogue verified. Extracted Studio/core JARs match build outputs. Required classes/icons, model exclusion and retained inference runtime/licences passed. The exact native launcher and ribbon component renders passed in an isolated profile. Applying the update to an isolated 0.1.69 installation replaced the application, preserved the runtime and passed native startup. Physical printing, live server/phone flows, manual DPI/accessibility and rollback remain separate checks. No live application, service or database was installed or restarted.

Local evidence: `SmartStudio/target/release-0.1.70-*`, `acceptance-0.1.70-update`, and `update-install-0.1.70`. Protected publisher configuration remains outside Git.
