# SmartStock 1.1.94 Windows release

Build 101094, prepared 28 October 2026 from the current shared workspace.
Includes the store-LAN receiving app and Status → Receiving Inventory Web App,
directly below Mobile Item Web App. Its activation QR opens Receiving directly.
Existing shared SmartStock changes are retained. No hosted application Worker
deployment or live store installation is part of this release.

Artifacts in `target/release-windows-1.1.94`:

- Update ZIP: `smartstock-windows-1.1.94.zip`, 168,593,629 bytes.
  SHA-256: `0b37f06a42285b8275a0808dcaa491b54da756ac17ab26f16aa26f720cd8538e`.
- All-in-one installer: `smartstock-windows-setup-1.1.94.exe`, 581,654,127 bytes.
  SHA-256: `9e244df3addcc5603402daa183b6b16f63888fafe4975c817947a37c46f2d78f`.
- Model migration bridge: `smartstock-ai-model-migration.jar`, 3,318 bytes.
  SHA-256: `ef37b72ca0b60a11d98ea9cd87df0b92813110939d951786b43efd4266253abd`.

Validation:

- Maven packaging and full test suite: 1,398 tests, zero failures/errors, 151 skipped.
- Receiving web tests: two passed, including duplicate-post retry and stock-change conflict handling.
- Repository security and whitespace checks passed.
- Model-free update archive and hosted model catalogue readiness passed.
- Exact ZIP isolated update, rollback, reapply, persistent profile preservation,
  bundled runtime, Receiving resources, JAR identity and native startup passed.
- Exact installer extraction, JAR identity, model-free contents and isolated native startup passed.

Model weights are unchanged and remain separate. Tests did not install the Windows
service or provision a live database. Store Wi-Fi/TLS and external-access blocking,
live pairing/revocation and receiving workflows, database/service behavior, physical
barcode scanners, printers, drawer and NFC remain rollout checks. macOS was not built.
Update the store server first, then registers.

Published 28 October 2026 to the Windows update feed and the permanent first-install
selection at https://downloads.deckers.gy/download/windows. Both artifacts passed
full hosted download, size and SHA-256 verification before selection. Independent
update metadata and installer selection readbacks passed, as did the public download
header check. Publication logs and metadata evidence remain in the local release directory.
