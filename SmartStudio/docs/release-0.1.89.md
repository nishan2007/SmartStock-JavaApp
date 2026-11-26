# SmartStudio 0.1.89 combined Windows quick update

Build 1089, prepared 28 October 2026 from the combined frozen source at
D:/SmartStock-combined-1.1.93/source, using its exact SmartStock 1.1.93 client.

Keeps Add barcode and Add QR code directly available in the Insert ribbon's
Codes group at compact window widths. Font and font-style dropdowns retain
their model and scroll position while browsing; selection refreshes no longer
interrupt open lists. Includes all other current shared Studio changes.

- Updater: SmartStudio-update-0.1.89.exe, 173,062,542 bytes.
- SHA-256: af54558bf5f9408b9e8ff71c4d4ac25104a8b310ad72d06a14097d7bd33cc89f.
- Maven: 338 tests, zero failures/errors, one skipped.
- Font regression coverage includes opening the real Swing dropdown, scrolling,
  refreshing selection, preserving the model/position and applying a font.
- Ribbon/layout regression checks pass for card and label workspaces at compact
  and wider window sizes. Packaged native startup passed.

Evidence: D:/SmartStock-combined-1.1.93/.
Quick-update feed only; no permanent first-install selection, macOS artifact or
live store installation. Save drafts before restarting and update SmartStock
first. Live paired workflows and physical printer/cutter checks remain separate.

Isolated 0.1.88-to-0.1.89 update passed, preserving the Java runtime and verifying
exact application/client JARs, native launch, model exclusions and model notices.

Published to the Studio Windows update feed on 28 October 2026 after complete
hosted download, size and SHA-256 verification. Independent metadata read-back
passed for version 0.1.89, build 1089, platform, publication status, object path,
size and hash.
