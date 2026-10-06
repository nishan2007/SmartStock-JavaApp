# SmartStock 1.0.247 — customer account payments and phone formatting

This Windows release packages the complete current application source and resources, including the catalog recovery changes shipped in 1.0.246.

Direct invoice payments now populate `customer_account_transactions.credit_applied_amount`, so rebuilding account balances includes their credit. Account loading repairs older zero-credit invoice payments only when their matching customer/invoice allocations equal the payment amount. It leaves unrelated payments untouched.

Customer account additions and edits save phone numbers in international format: `+countrycode` followed by digits. Seven-digit local numbers receive Guyana's `+592`; complete international numbers retain their country code. Spaces, brackets, dashes, and `00` prefixes are normalized. Missing phone numbers remain optional. Incomplete numbers, extensions, and multiple numbers are rejected with a clear message. Custom-order customer phone updates use the same server-side rule. Existing phone records are normalized on their next save.

Validation: Maven packaging ran the full suite: 840 tests, zero failures, zero errors, 46 skipped. The phone tests and custom-order phone persistence test passed. The invoice-credit PostgreSQL regression test was skipped because no test database was configured. Repository security and whitespace checks passed.

The full Windows installer was installed under an isolated temporary directory using a separate application profile. Verification confirmed the bundled Java launchers, server launcher, PostgreSQL installer, application JAR hash, phone-number library, and successful native-launcher welcome-window startup. The temporary installation was then uninstalled. This smoke check did not connect to a production store database.

Artifacts:

- Update ZIP: `smartstock-windows-1.0.247.zip`, 1,114,343,204 bytes; SHA-256 `4df75ff472843961379df3cfc5df645d229842f78f42451d05d0e383f10ea426`.
- Full installer: `smartstock-windows-setup-1.0.247.exe`, 1,520,744,986 bytes; SHA-256 `24fdae0fec7145f13e2624a54be28ed4196f0c5f0d8c4536ddd8fb716fd5b11b`.

Both artifacts were uploaded through the protected release publisher and downloaded completely from storage to verify byte size and SHA-256 before their release selections changed. Windows update metadata is published as build 100247; readback confirmed its version, size, SHA-256 and artifact path. The Windows installer selection was also published as 1.0.247 at https://downloads.deckers.gy/download/windows.

Production customer balances, store-server services, multi-store synchronization, backup/restore, printers, cash drawers, and NFC devices require separate live verification. This release does not itself apply an update to running store installations. macOS packaging was not performed on this Windows computer.
