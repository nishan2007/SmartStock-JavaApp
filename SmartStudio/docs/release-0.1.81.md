# SmartStudio 0.1.81 Windows quick update

Build 1081, published 26 October 2026 from the frozen 0.1.80 release source
with only portable badge serialization compatibility and its regression test
added. Uses the exact SmartStock 1.1.85 shared client.

Default rotation, visibility and transparency settings are omitted for
compatibility with older servers. Nondefault settings are retained and require
the SmartStock 1.1.85 server validation fix.

- `SmartStudio-update-0.1.81.exe`: 172,957,842 bytes.
- SHA-256: `d89485a94221dde894a1731693d17b47e888d8d895c33f18486ee6aa9251d93b`.
- Maven: 321 tests, zero failures/errors, one skipped.
- Packaged launcher startup passed.
- Isolated 0.1.80 to 0.1.81 installation passed: old application JAR removed,
  Java runtime preserved, exact app/core JARs verified and native launch passed.
- Extracted update excludes Java runtime and model weights; inference runtime
  and required model notices remain present.
- Exact Studio serializer to exact server save/read preserved layer settings
  and rejected personal photos.

Evidence and final publication logs/metadata are under
`D:/SmartStock-template-save-1.1.85/`.
The full hosted download matched size and SHA-256 before metadata publication;
independent read-back confirmed version, build, platform, object path, size,
SHA-256 and published status.
Update the store server to 1.1.85 first. Save/export drafts before restarting.
Quick-update feed only; the permanent first-install selection is unchanged.
No live store installation or macOS release. Live paired saves and physical
printer/cutter acceptance remain separate rollout checks.
