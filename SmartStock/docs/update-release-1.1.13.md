# SmartStock 1.1.13 — Integrated custom-order cancellation

Windows release, build 101013. Adds a server-enforced Custom Order Overrides cancellation action in Order Lookup. Its own dialog selects return stock treatment, refund method/reference and cancellation reason; the authenticated server settles all remaining lines and records cancellation within one transaction. Cancelled orders retain audit/status history and reject payment, production, delivery and reassignment changes.

Includes regression coverage for complete-line validation, unpaid/partially-paid/fully-paid settlement, retry safety, delivered/invoice-billed restrictions and cancelled-order mutations. The existing refund approval permission allows Custom Order Overrides; cancellation uses that same privileged permission.

Validation and exact artifact/publication evidence are recorded below. Live database, Windows service, cash drawer, printer, NFC and end-to-end installed-app cancellation acceptance remain separate checks. Update the server before registers. No running store installation is changed by publication.

Packaging gate passed: full Maven package/test run (890 tests, zero failures/errors, 47 skipped), repository security check and whitespace check. Model-free updater archive verification passed. The exact all-in-one installer was extracted; its application JAR SHA-256 matched the tested Maven JAR, and its bundled native launcher/runtime reached the welcome screen under an isolated development profile. The smoke process was stopped. The existing per-user installation was preserved; full installer registration/upgrade, live cancellation/refund/stock flows, service and hardware acceptance remain outstanding.

Artifact smartstock-windows-1.1.13.zip: 133322659 bytes; SHA-256 `9cb77571465ec7b06737b464dd18c90480a5bb3ae02f6875415b157aacca6931`.
Artifact smartstock-windows-setup-1.1.13.exe: 546674495 bytes; SHA-256 `74747f9333328f427e81800d6881f827741f9f78a56dece4435d64c3aa8d4610`.
Artifact smartstock-ai-model-migration.jar: 3318 bytes; SHA-256 `86762396ba73af2b6c6a37cf3669ad0d3512e694d41c64294726425c8f79f362`.

Publication completed: the uploaded update ZIP and all-in-one installer were fully downloaded and matched local byte sizes and SHA-256 before release metadata and latest installer selection changed. A separate protected read confirmed Windows update metadata version 1.1.13, build 101013, published status, content-addressed object path, size and SHA-256. The public installer link returned HTTP 200 with the expected 1.1.13 filename and 546674495-byte size.

Windows installer: https://downloads.deckers.gy/download/windows

No macOS artifact was built or published in this Windows release run. No running store was installed, updated or restarted.
