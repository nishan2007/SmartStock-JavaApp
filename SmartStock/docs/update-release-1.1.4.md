# SmartStock 1.1.4 — Swing quotation/invoice rendering correction

Numeric item cells use smaller text and padding so the installed Swing renderer leaves more space for descriptions. Footer totals and signatures use a separate table with independent widths. Unit price/original total omit trailing .00; discounts display at most two decimal places.

Validation: full Maven package suite, 864 tests, zero failures/errors, 47 skipped; security, whitespace, model-free package checks passed. Packaged application startup passed in an isolated user home. Layout visually inspected through the actual HtmlPagePrintable Swing printing class from the packaged JAR with representative school quotation items. Browser output was not used as print acceptance evidence.

Windows update ZIP: smartstock-windows-1.1.4.zip, 133284463 bytes. SHA-256: 3772a80322b7c10b38bbdb98a15be729a8d60e8c09294e55abb920cca143a0ef.

No new installer or macOS build. Applying the update to the live installed app and physical printing remain outstanding, along with live database/service, backup/restore, cash drawer and NFC checks. Publication does not apply the update to running stores. Update the store server before registers.

Published build 101004 after complete remote download matched the exact ZIP size and SHA-256. Published metadata response matched the verified artifact.
