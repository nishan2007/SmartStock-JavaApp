# SmartStock 1.1.46 (build 101046)

Windows release includes server handoff diagnostics, the side-chat drawer/register
continuity checks, and the server background-removal runtime improvements.
The production installation and computer name are unchanged.

Published 2026-10-18: Windows update metadata was read back and matched version
1.1.46, build 101046, object path, 167,088,028 bytes and the SHA-256 below. The
580,152,633-byte full installer was uploaded, fully downloaded and hash verified
before selecting it at https://downloads.deckers.gy/download/windows.

Validation completed:

- Full Maven suite: 1,103 tests, zero failures/errors, 73 skips.
- Latest handoff diagnostics/continuity tests: 11 tests, zero failures/errors.
- Repository security check and `git diff --check` passed.
- Final update archive passed the model-free layout and hosted-catalogue checks.
- Exact full installer installed successfully into the isolated validation folder.
- Installed bundled runtime verified local administrator denial, existing TLS
  identity reuse, handoff screen rendering, local report access, and a real
  temporary internet tunnel with synthetic data. Authenticated access passed;
  anonymous access returned 403. The temporary test connection was revoked.
  New temporary tunnel addresses needed a short DNS propagation interval.

Artifacts under `SmartStock/target/release-windows-1.1.46`:

- `smartstock-windows-1.1.46.zip`: SHA-256
  `ae16d58cbc8963812bb12b1eac4c81efb1f1ef6c43b603e78abdb9928996a4e8`.
- `smartstock-windows-setup-1.1.46.exe`: SHA-256
  `ffe3c0fda20b504c189c2c3e1f6711e1358a6a3b753525bcf779daf966f69b5d`.
- Model migration bridge and checksum are included separately. No AI model
  weights are inside the application update or installer.

Live A7 restore/cutover, production credential usability, register reconnection,
drawer balances and physical printers/NFC/drawers remain acceptance steps on the
actual computers. Full Best-model speed/quality benchmarking remains outstanding.
No live server installation, restart, rename or handoff was performed.
