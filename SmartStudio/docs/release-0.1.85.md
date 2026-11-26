# SmartStudio 0.1.85 Windows update

Build 1085, prepared 27 October 2026. Direct publication printing uses the same
300-DPI composed sheet as PDF export at actual physical size, caching one sheet
for repeated/banded callbacks. The print dialog's accepted printable area is
preserved instead of being replaced by a later generic capability query.

The customer confirmed the server's Evergreen template prints correctly via PDF
at 100%. A read-only, SHA-256-verified copy of that template was tested locally:
direct printing matches the PDF sheet raster pixel-for-pixel in two bands.
Customer artwork and server template archives remain outside versioned sources.

- Updater: `SmartStudio-update-0.1.85.exe`, 173,039,540 bytes.
- SHA-256: `dc94240dca77414d80940618d46a867711610d9b53fb5b5260cd20a0f5f938b4`.
- Exact SmartStock 1.1.91 client dependency retained.
- Maven: 324 tests, zero failures/errors, one skipped.
- Security and whitespace checks passed.

Acceptance evidence: `D:/SmartStock-studio-0.1.85`.
Isolated 0.1.84-to-0.1.85 update, installed JAR identities, runtime preservation,
native launch, model exclusions and runtime/notices checks passed.
Published 27 October 2026 after a complete hosted download, size and SHA-256
verification. Independent feed metadata verification passed.
Physical C5240 printing still requires a one-sheet check; the device is not
installed on the build computer. No live installation, service/database change,
printer output or macOS build was performed.
