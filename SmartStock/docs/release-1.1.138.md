# SmartStock 1.1.138 — transfer dropdown update

Windows update-only release, build 101138. Built from frozen published 1.1.137 source plus the transfer dropdown changes; unrelated working-tree changes were excluded.

Results include color, flavor, size and description when available. Adding/scanning clears the search and closes results. Empty searches cannot reopen the list. Hide results and Escape dismiss results and cancel pending searches.

Validation: full Maven package, 1591 tests, zero failures/errors, 205 optional skips; security and whitespace checks passed. Model-free archive verification passed. Exact isolated updater acceptance preserved the persistent model directory, backed up 1.1.137 and updated native launcher configuration. Isolated native launcher reached WelcomeFrame successfully.

ZIP: 170376870 bytes.
SHA-256: aa6f689a5828ae3acabe92d45db28fcdac0e5566c61647c445cd42b0cf91027e
Evidence: D:/SmartStock-release-builds/1.1.138.

No live application installation, service restart or database migration was performed. Update the store server before registers. Live scanner/database/service, printer/drawer/NFC and macOS checks remain outstanding. No full installer or model publication is included.

Publication completed: hosted ZIP downloaded in full and verified by size/SHA-256 before app release metadata creation. Independent metadata readback verified version 1.1.138, build 101138, object path, size and SHA-256. Existing first-install selection is unchanged.
