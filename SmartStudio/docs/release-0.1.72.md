# SmartStudio 0.1.72 Windows quick update

Build 1072, 23 October 2026, using SmartStock client dependency 1.1.80.

Includes the approved rounded Task Ribbon refinements, labelled text controls and consistent specialist output actions; the centered staff sign-in card; configured company-logo branding in login/editor headers; and exact Creative Brush v4 application identity. Authentication, badge/PIN handling, permissions and offline restrictions are retained. NFC/connection messages use real checks.

Artifact: `SmartStudio-update-0.1.72.exe`, **171,283,366 bytes**.
SHA-256: `a6007d2d14eaac6b0f43bf2b8fb4a8a03f71f59905863c14656b971e32553f13`.

Update-only executable for an existing SmartStudio installation. Java runtime and AI model weights excluded; inference runtime and notices retained. No first-install portal selection, live store/server installation, phone-page deployment or database change.

Validation: Studio package/test build ran 281 tests, no failures/errors, one skipped. Security and whitespace checks passed. Hosted model catalogue readiness passed. Exact extracted Studio/core JARs match build outputs; required login/icon resources, model exclusion and retained licences/runtime passed. Native startup and ribbon component rendering passed in an isolated profile. The extracted executable's icon was inspected and matches Creative Brush. Login, NFC fallback and icon container tests passed; no claim of physical reader authentication is made. Native macOS packaging, installed icon-cache behavior, live paired authentication/NFC/PIN, manual DPI/accessibility, physical printing and rollback remain separate checks.

Evidence: `SmartStudio/target/release-0.1.72-*`, `acceptance-0.1.72-update`, `update-install-0.1.72`, and `approved-login-proof`. Protected publisher configuration is outside Git.

Published successfully. The publisher downloaded the complete hosted executable and verified its exact size and SHA-256 before writing release metadata. Independent verification matched version, build, platform, published status, path, size and hash. Isolated 0.1.71 → 0.1.72 upgrade passed: application replaced, Java runtime preserved, native startup passed. No first-install selection or live installation was changed.
