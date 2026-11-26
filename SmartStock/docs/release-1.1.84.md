# Windows quick update 1.1.84

Published 25 October 2026, build 101084. Includes the completed Orders redesign
and Work Queue, together with the completed shared-source changes from 1.1.83.
Source was frozen before packaging; subsequent cross-chat edits are recorded in
`D:/SmartStock-release-1.1.84/changes-after-freeze.txt` and were not mixed into
this tested release.

- Artifact: `smartstock-windows-1.1.84.zip`, 168,493,561 bytes.
- SHA-256: `b1e3808292fbb0b4f07e831e5fe67c92f2d246a1e5c71ed551b93a21bdaf1fd5`.
- Object: `updates/windows/1.1.84/b1e3808292fbb0b4f07e831e5fe67c92f2d246a1e5c71ed551b93a21bdaf1fd5/smartstock-windows-1.1.84.zip`.
- Frozen-source Maven package: 1,346 tests, zero failures/errors, 143 skips.
- Security and whitespace checks passed.
- Exact ZIP passed layout/version, model exclusion, runtime/notices, migration
  support and hosted model catalogue verification.
- Isolated update, rollback, reapply, persistent profile preservation, bundled
  runtime, required resources, installed JAR identity and native launcher passed.
- Full hosted download matched size and SHA-256 before metadata creation;
  independent metadata read-back passed.

Release evidence is on D because C ran out of space during acceptance:
`D:/SmartStock-release-1.1.84/`. It contains the frozen source manifest,
release/validation.json, publish-stock.log and release-1.1.84-metadata.json.

Quick update only. The permanent first-install selection was unchanged. No
macOS build or live store installation was performed. Update the store server
first, then registers. Live database/service, signed-in workflows, remote phones,
printer, drawer, NFC, print/cut and separately hosted model-download acceptance
remain separate rollout checks.
