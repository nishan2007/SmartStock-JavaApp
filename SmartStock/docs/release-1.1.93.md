# SmartStock 1.1.93 combined Windows quick update

Build 101093, prepared 28 October 2026 from the current shared workspace frozen
at D:/SmartStock-combined-1.1.93/source. All 2,494 captured files matched the
workspace before release. Source manifest and comparison against 1.1.92 are
retained in that release folder.

Adds item/size pictures below Price Reason, variant-to-item photo fallback,
and asynchronous hover previews in custom-order search suggestions. Includes
all other current shared application changes. Studio 0.1.89 uses the exact
SmartStock JAR from this build. Hosted Worker deployments remain separate.

- ZIP: smartstock-windows-1.1.93.zip, 168,586,355 bytes.
- SHA-256: 04a729938e35a49e86e11faee892fed1aa3ded73c3a4253b49e31dcc70853134.
- Migration bridge SHA-256: 11815c089240d800c4d7c4a6ceab4478ba2f29c6826a2e871d4fb202aa3ef18d.
- Maven: 1,394 tests, zero failures/errors, 151 skipped.
- Web/Worker tests: 144 passed. Website: 70 passed; TypeScript passed.
- Security and whitespace checks passed.
- Model-free archive verification and hosted model catalogue readiness passed.
  Model weights remain separate and unchanged.

Quick-update feed only: no first-install selection, macOS artifact, hosted
Worker deployment or live store installation. Live database/service workflows,
photo delivery on paired registers, printers, drawer and NFC remain rollout
checks. Update the store server first, then registers.

Exact ZIP isolated update, rollback, reapply, persistent profile preservation,
installed JAR/resources/runtime and native startup checks passed.

Published to the Windows update feed on 28 October 2026 after full hosted
download, size and SHA-256 verification. Independent metadata read-back passed
for version 1.1.93, build 101093, platform, publication status, object path,
size and hash. Object path uses the Bash publisher's windows/ prefix.
