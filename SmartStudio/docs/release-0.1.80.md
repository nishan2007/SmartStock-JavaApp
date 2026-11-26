# SmartStudio 0.1.80 Windows quick update

Published 25 October 2026, build 1080, using the exact SmartStock 1.1.84 shared client from its update ZIP.
This release packages the completed Studio changes from the frozen combined
source. Later edits in other chats are outside this release snapshot.

- `SmartStudio-update-0.1.80.exe`: 172,956,867 bytes.
- SHA-256: `3fcbdbe95dd0995a88fd9b0124c9f48a4e9291919be8b5e6478b764eb94dc009`.
- Maven: 320 tests, zero failures/errors, one skip.
- Exact installer extraction confirmed app/core JAR identity, required feature
  classes, inference runtime/notices and absence of model weights or Java runtime.
- Native packaged/extracted launch and exact-artifact ribbon rendering passed.
- Isolated 0.1.79 to 0.1.80 installation succeeded, removed the old app JAR,
  preserved the existing Java runtime and produced the updated login preview.
  Slow D-drive startup exceeded the harness's initial 30-second limit; eventual
  native startup and process completion were verified separately.

Release evidence is in `D:/SmartStock-release-1.1.84/`, including
studio-validation.json, studio-installed/update.log and publish-studio.log.

Quick-update channel only. The permanent first-install selection is unchanged.
No macOS release or live store installation. Export drafts before restarting.
Live shared-save, signed-in business flows and physical printer/cutter acceptance
remain separate rollout checks.

Full hosted download matched size and SHA-256 before metadata publication. Independent metadata read-back verified version, build, Windows platform, published status, object path, size and SHA-256.
