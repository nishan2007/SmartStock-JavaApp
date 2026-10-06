# SmartStock 1.1.5 — continuous quotation and invoice footer

Item rows, signature rows, stacked subtotal/grand total and footer notes share one continuous grid. Removes the table join gap/doubled border and nested signature label dividers. Labels use consistent padding and readable text. Narrow quantity and wider description are preserved.

Validation: Maven packaging, 864 tests, zero failures/errors, 47 skipped. Security, whitespace, model-free package verification and isolated packaged-app startup passed. Actual Swing print renderer was used to inspect the approved layout; letter-size PDF preview approved by user before packaging.

SHA-256: cc930af5542dd8a818699410d3d9fe3695ec4000099bedec09c9f2e7a8e067c3. Update-only Windows ZIP; no new installer or macOS artifact. Live installed-app update, physical printing, database/service, backup/restore, drawer and NFC checks remain separate verification steps. Update server before registers. Publication does not update running stores.

Published Windows build 101005. Complete remote download verified 133284492 bytes and the SHA-256 above before metadata publication; response matched verified artifact.
