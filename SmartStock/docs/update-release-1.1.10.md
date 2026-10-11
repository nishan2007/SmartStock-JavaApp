# SmartStock 1.1.10 — CT221B price-tag polarity

Fixes the TSPL bitmap encoder for CT221B labels. Black pixels clear bits; white pixels and unused right-edge padding retain set bits. This restores black text and barcodes on white paper. ESC/POS receipt bitmap encoding remains unchanged.

Validation: Maven packaging suite passed (877 tests, zero failures/errors, 47 skipped). A byte-level regression checks white rows, black rows, mixed pixels and a partial final byte. Price-tag and receipt-printer tests passed. Repository security and whitespace checks passed. Model-free archive verification passed, and the packaged JAR matches the tested JAR. An isolated extracted update using the installed native launcher/runtime opened the welcome window with a temporary development profile; the process was stopped.

Artifact: `smartstock-windows-1.1.10.zip`, 133,312,405 bytes; SHA-256 `686c33068c3e0d87f343e1eb0c6abab3b0b523b9acef945903605846f2047174`. Windows build 101010. Verified publishing checks hosted model availability and downloads the complete stored artifact before release metadata insertion.

Update-only release; no new installer or macOS package. A physical CT221B test print and barcode scan remain required. Installed-app update, live database/services, backup/restore, receipt printer, cash drawer and NFC checks remain separate. Update the server before registers. Publishing does not apply updates to running installations.

Publication completed: the complete stored download matched the byte size and SHA-256 above before metadata insertion. The published response matched Windows update 1.1.10, build 101010 and its verified artifact.
