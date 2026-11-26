# Windows quick update 1.1.83

Published 25 October 2026, build 101083. Consolidates completed work from the
shared repository across the Sales Tasks, Balance Sheet, photo-review,
customer-order retry/phone lookup, web Orders/editor, overseas/time-clock,
shirt/polo preview, and shared publication/font/rendering work.

## Scope

The source was frozen in an isolated release snapshot before building. The active
Orders-screen redesign was excluded: its changed detail/list panels and new
design/workspace classes are absent from the artifact. The prior Orders screen
and completed combined Work Queue were retained, together with the completed
existing-customer submission retry fix. The live working tree was preserved.

SmartStudio was separately published as 0.1.79 with this exact shared client.
The completed Time Clock and Orders gateway Workers were deployed and their live
entry/script responses checked. Existing website shirt/polo deployment was not
replaced. Balance Sheet and other new store-server endpoints become available
after updating the store server.

## Exact artifact

- `smartstock-windows-1.1.83.zip`: 168,184,674 bytes.
- SHA-256: `a682f9b108d00cf1cddd48aab05a70ff267b19f78f23498475c29eafc21130e8`.
- Update-feed object: `windows/1.1.83/a682f9b108d00cf1cddd48aab05a70ff267b19f78f23498475c29eafc21130e8/smartstock-windows-1.1.83.zip`.

## Validation

- Frozen-source Maven package: 1,335 tests, zero failures/errors, 142 opt-in or
  platform skips. Completed web/gateway checks: 46 passed. Security and whitespace
  checks passed.
- Exact ZIP passed layout/version, model exclusion, runtime/notices, migration
  support, and hosted model catalogue checks.
- Isolated installed-app update, rollback, reapply, native launcher configuration,
  persistent profile preservation, bundled runtime, and required feature/schema
  resources passed. Native setup launcher remained running in an isolated home.
- Full hosted download matched size and SHA-256 before metadata publication.
  Independent release metadata read-back verified version, build, platform,
  immutable object path, published status, size, and SHA-256.
- Evidence: `target/release-windows-1.1.83/validation.json`, `publish.log`,
  `target/release-1.1.83-metadata.json`, and
  `target/release-acceptance-1.1.83/source-manifest.json`.

Quick update only; permanent first-install selection was unchanged. AI model
weights remain separately hosted. No macOS release or live store installation
occurred. Update the store server first, then registers. Live database/service
upgrade, signed-in business flows, remote phones, printers, drawers, NFC, and
physical print/cut acceptance remain separate rollout checks.
