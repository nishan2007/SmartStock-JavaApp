# SmartStock 1.1.55 Windows release

Build 101055. Update published 19 October 2026; independent hosted metadata matches exact verified bytes. Includes quotation custom-item saved
variant prices and custom-order Called / Called - No Answer tracking.

The standard Windows release packager produced the model-free update ZIP and
all-in-one first-install EXE. Maven packaging: 1142 tests, 90 skipped, zero
failures/errors. Security and whitespace checks passed. The exact update passed
model-free validation and isolated native-launcher acceptance.

The call-status SQL migration was applied twice to a disposable PostgreSQL 17
instance on loopback port 56555. Existing PENDING and DELIVERED rows survived,
CALLED and CALLED_NO_ANSWER inserted successfully, and INVALID was rejected by
the constraint. The disposable instance was stopped after verification.

Update ZIP: target/release-windows-1.1.55/smartstock-windows-1.1.55.zip
Size: 167059765 bytes
SHA-256: 9d2e0906bceead285d767dcb4c756712732f42a8125b069f24adc9c80ed6fd36

Installer: target/release-windows-1.1.55/smartstock-windows-setup-1.1.55.exe
Size: 580122059 bytes
SHA-256: 3e9f3dcc59f6898526dc2745bec233a120e2c23eb86ef920f56419cd7cc8da28

Migration bridge SHA-256:
685488f6e64c87df0d3420aabe446e3029364ba04096555b31f6f3b506c55aa7

Both the updater and first-install EXE were published after full hosted download,
size and SHA-256 verification. Independent update metadata matched version,
build, object path, size and hash. Installer portal: https://downloads.deckers.gy/download/windows
Evidence: target/release-1.1.55-publish.log, installer-publish.log and metadata.json.

No running store was installed, restarted, or upgraded. Live store database
migration, LAN permissions/sessions, Windows services, printers, drawer, NFC,
full installer execution and installed upgrade/rollback remain separate
acceptance checks. No macOS artifact was built. Update the server before
registers. Models were unchanged and remain outside the application package.
