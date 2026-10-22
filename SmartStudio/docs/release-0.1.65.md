# SmartStudio 0.1.65

Windows build 1065, published 22 October 2026. Includes the newly built
SmartStock 1.1.73 shared client/print-engine JAR. Only the SmartStudio installer
is published by this release; no SmartStock server/register update is published.

## Changes

- Template-only printing leaves selected numbering fields blank while preserving
  artwork, other merge fields and the saved template.
- Numbering can traverse rows or columns from any corner, or follow a custom
  position permutation. Position map identifies the physical slots.
- Cut and stack assigns consecutive ranges to each cut position. For 1,000
  records and two positions, 500 sheets hold 1–500 in the first position and
  501–1000 in the second. Incomplete piles leave blank slots; duplex records
  remain paired. Mixed-page/booklet/skipped-slot combinations are rejected.
- Position order and stacking settings save with templates and are shared by
  Preview, Print and Export PDF. Print setup scrolls to expose all controls.

## Validation

The full shared-core build ran 1,236 tests with zero failures/errors and 111
skipped. All 236 Studio tests passed with no skips. Repository security,
whitespace and hosted model catalogue checks passed. Snapshotted release
inputs remained unchanged through building, packaging and publication staging.

Standard Windows packaging produced the portable ZIP and installer. Exact
installer extraction, Studio/core JAR identity, sequence-engine inclusion,
model-free scan and isolated native launcher startup passed. A proof harness
using the exact extracted JARs verified the 500-sheet cut-and-stack job,
template-only output and print-setup rendering; its screenshot was reviewed.

## Artifacts

Installer: `SmartStudio-setup-0.1.65.exe`
Size: 190270685 bytes
SHA-256: `c925022e822fdb99141610e08cb95d556d5f79d1da5a7d6eb4faf381368a270f`

Portable ZIP: `SmartStudio-0.1.65-windows-20261022-121236.zip`
Size: 192658689 bytes
SHA-256: `ade7c55d677aad0b67064b559c91712781ff19849c43c478108da6ccdebd31fd`

Evidence: `SmartStudio/target/release-0.1.65-*` and
`SmartStudio/target/acceptance-published-0.1.65`.

The complete hosted installer was downloaded and size/SHA-256 verified before
release metadata publication. Independent metadata readback matched version,
build, platform, published status, object path, size and hash. The protected
portal selection was updated while preserving other settings (deployment
`0c665159-f638-42b1-a519-7238c1425e7c`). A separate full download from
https://downloads.deckers.gy/download/smartstudio/windows matched the exact
installer size and SHA-256 above.

Live installation, upgrade/rollback, physical printer alignment and face-up/
face-down stack order remain separate checks. No running application or service
was installed or restarted. No model weights or macOS package were published.
