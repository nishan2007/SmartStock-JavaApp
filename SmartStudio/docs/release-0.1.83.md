# SmartStudio 0.1.83 Windows quick update

Build 1083, prepared 27 October 2026 from all current shared Studio sources at
D:/SmartStock-combined-1.1.91/source, using the exact SmartStock 1.1.91 dependency.

Fixes multi-page navigation remaining collapsed after single-page templates.
Billbooks, business cards, labels and banners retain page navigation. Includes
other current shared Studio changes.

- SmartStudio-update-0.1.83.exe: 173,039,313 bytes.
- SHA-256: 363e3ee005f5107e67735b5a25b3dfae23bd2fae7421f4104e32ccecde8c06817.
- Maven: 322 tests, zero failures/errors, one skipped.
- Packaged native launcher startup passed.
- Packaged shared client hash matches the exact SmartStock release JAR.

Evidence: D:/SmartStock-combined-1.1.91/.
No live installation or macOS build. Real paired saves, clipboard/drag operations,
physical printer/cutter and store network access remain separate rollout checks.
Save drafts before restarting. Update SmartStock first.

Isolated 0.1.82 to 0.1.83 update acceptance passed: old Studio JAR removed,
Java runtime preserved, exact Studio/core JAR identities and native launch passed.
Extracted updater excludes Java runtime and model weights, retaining inference
runtime and model notices. Frozen source comparison found no later edits.

Published 27 October 2026 after complete hosted download, size and SHA-256
verification. Independent update-feed metadata verification passed for version,
build, platform, published status, object path, size and hash.
