# SmartStudio 0.1.74 Windows quick update

Build 1074, 24 October 2026, SmartStock client dependency 1.1.80.

Print Setup Preview now opens a visible, screen-bounded local sheet window with artwork and sheet navigation. Close/Escape does not save or contact the server; printing retains a separate confirmation workflow. Bill Books Numbering now exposes print content, number fields, numbering order, custom positions, position map, cut-and-stack and Preview directly in the ribbon. The numbering popup and duplicate paper/margin rows are removed. Shared-template acknowledgement checks remain enforced; mismatch messages clarify that the draft is unverified and recommend exporting a local copy before reopening/retrying. A store/Studio format mismatch remains a separate server compatibility issue.

Artifact: `SmartStudio-update-0.1.74.exe`, **171,287,716 bytes**.
SHA-256: `1898d5dc442cdadf21d31bb19f88617278d56850d5199ca2f9d4c7f8e109d07b`.

Update-only executable for an existing installation. Java runtime and model weights excluded; inference runtime/notices retained. No full installer or first-install portal selection changed. No store-server deployment, live installation or database change.

Validation: Studio package/test build ran 284 tests, no failures/errors, one skipped. Security, whitespace and hosted-model catalogue readiness checks passed. Exact extracted JARs, required resources, model exclusion and retained licences/runtime passed. Native launcher and ribbon rendering passed in an isolated profile. Isolated 0.1.73 → 0.1.74 upgrade replaced the app, preserved Java and passed startup. The Preview regression test renders synthetic artwork on multiple sheets without server calls or draft saving; acknowledgement mismatch still fails safely. Numbering checks cover all output modes, original artwork preservation and both target screens at 100%, 125% and 150% logical budgets. Physical printing/NFC, live paired authentication, manual DPI/accessibility, native macOS packaging and rollback remain separate checks.

Evidence: `SmartStudio/target/release-0.1.74-*`, `acceptance-0.1.74-update`, `update-install-0.1.74`, `print-preview-fixed.png` and `numbering-ribbon.png`. Protected publisher credentials remain outside Git.

Published successfully. The complete hosted executable was downloaded and matched the exact size and SHA-256 before update metadata was written. Independent metadata verification matched version, build, platform, published status, path, size and hash. No first-install selection or live installation changed.
