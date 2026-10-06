# SmartStock 1.1.7 — Custom-order cart editing and attachments

Packages the current application source and resources, including the Compact quotation/invoice layout in 1.1.6. Custom-order cart rows can be edited and replaced through Update Line, preserving attachments and restoring item fields, discounts and add-ons. Cancel Edit preserves the original row. The attachment window accepts Explorer file drops with duplicate filtering and a 15-file limit. Later payment inputs accept comma-formatted amounts and request errors appear directly.

Validation: full Maven packaging suite passed: 870 tests, zero failures/errors, 47 skipped. Website build and 38 tests passed. SmartStudio Maven tests passed. Repository security and staged secret-pattern checks passed. Staged whitespace checks passed after rebuilding the source-matched website bundle. Update archive verification passed, and its JAR matches the tested JAR. An isolated copy using the installed native launcher/runtime opened the welcome window with a temporary development profile; it was then stopped.

Windows update artifact: `smartstock-windows-1.1.7.zip`, 133,294,334 bytes; SHA-256 `2cedb96a7ca5790224ccabded6cab6b6709779ba211473315184f9a150c2019f`. Build 101007. AI model weights remain separately hosted and are checked by the verified publisher.

No new installer, Studio release, or macOS package was published. Production update application, custom-order editing/uploads/payments, database/service, backup/restore, physical printing, cash drawers and NFC require live verification. Update the store server before registers. Publishing does not automatically apply the update to installations.

Publication completed: the entire stored update download matched the byte size and SHA-256 above before release metadata insertion. The published response matched Windows version 1.1.7 and its verified artifact.
