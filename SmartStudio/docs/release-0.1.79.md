# SmartStudio 0.1.79 Windows quick update

Published 25 October 2026, build 1079. Shared SmartStock client dependency 1.1.83.

Consolidates completed work in the current source with the current shared
publication/font/rendering client. Existing editing, PDF, ribbon, numbering,
save read-back compatibility, and print/cut functionality remain included.

## Exact artifact and acceptance

- `SmartStudio-update-0.1.79.exe`: 172,653,452 bytes.
- SHA-256: `5d29a03bdfa1d40281a84cfeb37c316718a72ce7a8c5ed3842863833c368c0a9`.
- Frozen-source Maven package: 320 tests, zero failures/errors, one skip.
- Extracted exact installer confirmed application/core JAR identity, required
  classes, inference runtime/notices, and absence of model weights or Java runtime.
- Native startup and ribbon/workspace rendering passed. Isolated 0.1.78 → 0.1.79
  installation replaced the application, retained Java, and passed native launch.
  Shortcut files were preserved around the acceptance run.
- Publisher fully downloaded the hosted object and verified size/SHA-256 before
  creating metadata. Independent metadata read-back matched version, build,
  platform, published status, immutable object path, size, and SHA-256.

Quick update channel only. Permanent first-install selection was unchanged.
No live store/application installation occurred. Export open drafts before
restarting. Live shared-save and physical printer/cutter acceptance remain
separate checks. No macOS release was built or published.
