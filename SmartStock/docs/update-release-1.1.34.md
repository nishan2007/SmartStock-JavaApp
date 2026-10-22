# SmartStock 1.1.34 Windows

Build 101034 fixes cross-store time-clock correction delivery. Payroll corrections
made against a copied shift are sent as explicit audit commands to the owning
store. That store checks current management permission and original punches,
recalculates the shift locally and republishes its authoritative record. Historical
locally authored corrections are picked up automatically. UUID-based replay
protection prevents duplicate application; conflicts remain failed inbox events
for review. Ordinary foreign snapshots still cannot overwrite local shifts.
See [correction synchronization](time-clock-correction-sync.md).

Both store servers must include this release for delivery. Publishing does not
install, restart or update either server. Update servers before registers.

Employee Time Clock also refreshes automatically and updates the selected day's
details when a fresh dashboard arrives.

Validation: packaging Maven gate ran 1,027 tests, 61 skipped, no failures/errors.
The correction integration test ran against its own disposable PostgreSQL cluster,
including historical announcement, owner application, permission rejection,
conflict rejection, idempotent replay and foreign-snapshot protection. Repository
security and whitespace checks passed. Model-free updater validation and hosted
model catalogue readiness passed.

All 30 updater files match the extracted all-in-one installer payload. Its native
Windows launcher opened the welcome window in an isolated development register
profile; the test process was stopped. No live store application was installed.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 166968417 | cc65728e059da5b64b758047128c0497f737200e2b92a05350617be378a86b8e |
| All-in-one installer | 580044729 | 9df103a9da3c63789bb92106a99df490cb046dac4c857cc1b5b9f972f85009c6 |
| Model migration helper | 3318 | 51b534dfc8ea423f8a9f7ebbf52bd8dbebcd62613e9559bf0d2a5d175b83584a |

Publication completed. Both artifacts were uploaded and downloaded in full; exact
sizes and SHA-256 values matched before release metadata or installer selection
changed. Update metadata readback matched version 1.1.34, build 101034, published
status, content-addressed object path, size and SHA-256. The public installer HEAD
check returned HTTP 200, filename `smartstock-windows-setup-1.1.34.exe` and
580044729 bytes at https://downloads.deckers.gy/download/windows.
These bytes must not be rebuilt under this published version.

Live acceptance of the affected correction on the owning store and employee
screen remains outstanding, along with live service, update/rollback, register
connectivity and printer, drawer and NFC checks. macOS requires separate packaging
and verification on a Mac. SmartStudio is not part of this release.
