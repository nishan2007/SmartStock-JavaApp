# SmartStock 1.1.96 combined Windows release

Build 101096, prepared 28 October 2026. Frozen combined source and verification
evidence: D:/SmartStock-combined-1.1.96. Includes all shared application changes
from the previous combined release, plus CT221B USB label sharing, receipt
connection retries before transmission, readable Retry buttons, and consistent
whole-dollar invoice payment totals. Hosted web apps and AI models are unchanged.

- Update ZIP: 168631434 bytes; SHA-256 e6818a183d8e67e31d422f3015e8fc3d2d3cf56cab85f230cf4ac501e4dde5c0.
- All-in-one installer: 581695294 bytes; SHA-256 146215e504d7de986449db312e3130036182a7e04d6335eb5b5269df5027766d.
- Migration bridge SHA-256: 86b577a6a07bd0512e5aad7a5e29e8004c6153afbaf8eadd63b73e4c867845aa.
- Full Maven suite: 1,411 tests, zero failures/errors, 151 skipped.
- Security and whitespace checks passed. Hosted model catalogue and model-free
  archive checks passed; runtime and notices retained.
- Exact ZIP update, rollback, reapply, persistent profile/model-marker preservation,
  installed JAR/resources/runtime and native startup passed in an isolated copy.
- Exact installer extraction, JAR identity, Java/PostgreSQL/launcher/notice
  presence, model exclusions and isolated native startup passed.

Published 28 October 2026 after complete hosted download, size and SHA-256
verification of both the updater and all-in-one installer. Independent update
metadata read-back confirmed version/build, platform, published status, exact
object path, size and hash. The public Windows installer selection matches the
verified 1.1.96 filename and exact size. No live store was installed, updated or
restarted. Live database/service installation, paired workflows, physical CT221B,
receipt printer, drawer and NFC acceptance remain rollout checks. macOS packaging
and verification remain outstanding. Update the server before registers.
