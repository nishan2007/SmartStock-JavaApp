# SmartStudio 0.1.92 combined Windows quick update

Build 1092, prepared 28 October 2026 from D:/SmartStock-combined-1.1.96/source.
Includes all combined Studio changes and the exact SmartStock 1.1.96 dependency.

- Updater: SmartStudio-update-0.1.92.exe, 173118973 bytes.
- SHA-256: 74eede7a35753a3f662e2c1f2eaa12ee8325cc0d7404b2bfb1369eb76bb97af9.
- Full Maven suite: 344 tests, 0 failures, 0 errors, 1 skipped.
- Repaired a test-only OCR PID marker race by publishing the marker atomically;
  production OCR behavior is unchanged. The complete suite passed afterward.
- Isolated 0.1.91-to-0.1.92 update, runtime preservation, exact installed Studio
  and SmartStock JARs, native launch, model exclusions and notices passed.
- Security and whitespace checks passed. Acceptance refreshed two existing
  shortcuts to the isolated test folder; both were corrected and verified against
  the registered live installation. Live application files were unchanged.

Published 28 October 2026 after complete hosted download, size and SHA-256 verification. Independent metadata read-back confirmed version/build, platform, published status, exact object path, size and hash. This is an updater for existing Windows
installations. No live installation or macOS artifact. Live paired workflows and
physical printer/cutter/scanner checks remain rollout steps. Save drafts before
restarting.
