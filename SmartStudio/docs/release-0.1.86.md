# SmartStudio 0.1.86 Windows update

Corrects the native print-dialog setup: the default printer's hardware area is
no longer supplied before selecting a printer. If the dialog supplies no area,
capabilities are queried only from the selected printer afterward. Explicitly
requests the sheet orientation and preserves a selected area. Print stays inside
SmartStudio; the experimental PDF-reader detour was removed before packaging.

Includes original-resolution/vector PDF export and geometry-only local print
diagnostics. Print Setup / Copy print details copies the latest report. Reports
contain printer/Java version, selected attributes, page dimensions, callback
transforms and clipping bounds, without document artwork or merge data.

- Updater: `SmartStudio-update-0.1.86.exe`, 173,049,130 bytes.
- SHA-256: `55357b54174e7eef6f528d0da2e2da57d9f45abef531dcaa15ab7011daafb0e5`.
- Build 1086; exact SmartStock 1.1.91 client dependency retained.
- Full Studio suite passed, then focused tests reran after the diagnostic-copy
  control was added. Combined reports: 332 tests, zero failures/errors, one skip.
- Security and whitespace checks passed.

Acceptance evidence: `D:/SmartStock-studio-0.1.86`.
Isolated update, exact installed JARs, runtime preservation, native startup and
model/runtime/notices checks passed.
On 27 October 2026, one Evergreen 12-card Letter sheet was submitted through the
corrected direct Java print path to Canon iR2525/2530 UFRII LT at 10.1.1.13.
The user confirmed the physical print worked. The C5240 is on another computer
and still requires its own verification. No live installation or macOS build was
performed.

Published to the Studio Windows update feed on 27 October 2026 after full hosted
download, size and SHA-256 verification. Independent feed metadata verification
passed for version 0.1.86, build 1086, artifact path, size, hash and published status.
