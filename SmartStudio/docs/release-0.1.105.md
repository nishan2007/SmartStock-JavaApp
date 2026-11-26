# SmartStudio Windows 0.1.105 — 2026-11-17

Cumulative creative application release with current editing, PDF, template,
badge, banner, print/preview, clipboard, layer, table, ribbon and workspace source.
Uses the SmartStock 1.1.123 client. SmartDrive quick update was excluded.

Full Maven package suite passed: 380 tests, zero failures/errors, one optional
skip. Packaged native startup passed. Exact first-install EXE payload was extracted
and independently checked: application/dependency JAR identities, retained ONNX
runtime/notices, model exclusion and isolated native startup passed. Studio's
client JAR has identical decompressed contents to the final SmartStock JAR; ZIP
timestamps differ because Maven packaged it twice. No installer registry/shell
actions or in-place Studio update were exercised.

Published artifact: `D:/SmartStudio-release-0.1.105/SmartStudio-setup-0.1.105.exe`

- Build: 1105
- Size: 195085797 bytes
- SHA-256: 385eb16be338c7c9a58a948d4c6947ab79075bd1704035b128061d1fb43ed0df

Protected publisher verified complete hosted download/size/SHA-256 before release
metadata publication. Independent release-row readback matched all artifact fields.
The public Windows installer selection was updated after verification; a complete
download from the selected public URL independently matched the same size/hash.
The same full installer serves the Studio update channel.

No live app was installed or restarted. Physical printer/cutter, live store
database/service workflows, installer/shell registration, actual in-place update,
real model download/offline inference and macOS checks remain outstanding.
