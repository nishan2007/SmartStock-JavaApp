# SmartStock 1.1.14 — Customer-supplied cancellation fix

Windows release, build 101014. Preserves SQL NULL for the inventory item ID in custom-order line returns and skips restocking customer-supplied items. Regression tests cover paid customer-supplied cancellation with No Restock and Restock selections, alongside existing financial settlement and cancellation guards.

Exact artifact and publication evidence will be recorded below. Installed upgrade/registration, live cancellation/refund/database behavior, Windows services, printers, cash drawer, NFC, model download/migration and rollback remain separate acceptance checks. Publication does not install or restart any running store. Update the server before registers.

Validation passed: Maven packaging/test suite (891 tests, zero failures/errors, 47 skipped), security check, whitespace check and model-free update verification. The exact installer was extracted, its application JAR matched the tested Maven artifact by SHA-256, and its bundled native launcher/runtime reached the welcome screen with an isolated development profile. The verification process was stopped.

Artifact smartstock-windows-1.1.14.zip: 133322625 bytes; SHA-256 `71ce08c72f1387072b5bc84caf127fa346b2b9c9faa53699e2cb63f90a827464`.
Artifact smartstock-windows-setup-1.1.14.exe: 546672860 bytes; SHA-256 `af23ca7aa4eaf2b2572a7d4cab10d16ea53a5dd1f3620f149bd5778b856314ff`.
Artifact smartstock-ai-model-migration.jar: 3318 bytes; SHA-256 `be144f7f4ce7b1ebcbb3182327885ad8f3b35155e9f9fee201f29cf09754cb78`.

Publication completed: both hosted artifacts were fully downloaded and their sizes and SHA-256 matched local files before update metadata and latest installer selection changed. A separate protected metadata read confirmed version 1.1.14, build 101014, published status, object path, size and hash. The public Windows installer link returned HTTP 200 with the expected 1.1.14 filename and 546672860-byte size.

Windows installer: https://downloads.deckers.gy/download/windows

No macOS artifact was built or published. No running store was installed, updated or restarted.
