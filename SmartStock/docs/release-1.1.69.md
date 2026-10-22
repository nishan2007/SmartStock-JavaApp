# SmartStock 1.1.69 combined Windows release

Build 101069 packages the cumulative shared workspace, including Sublimation
product marking/filtering and website section, shirt-preview changes, account
balance/signature receipts, saved photo print records, overseas orders and
shared bill book server support. The website bundle was freshly built before
the application package. The ordered Sublimation migration and base schema are
both present in the exact installer.

Published 21 October 2026. The complete hosted update and installer were each
downloaded and verified for size and SHA-256 before release metadata or public
installer selection changed. The in-app release row was independently verified.
An independent complete public installer download also matched the exact size
and SHA-256 above. Evidence is retained under target/release-windows-1.1.69.

Update ZIP: smartstock-windows-1.1.69.zip
Size: 167304045 bytes
SHA-256: 749f0988108982c1cb2a9ef20ed62d1afa2911720c1a189789112f68665d13d9

All-in-one installer: smartstock-windows-setup-1.1.69.exe
Size: 580360570 bytes
SHA-256: beb9f3c50ddcd1a6090d03530618ebf9238ea9f8e5f1b74597fc7b6f6013dc14

Validation: 1182 core tests, no failures/errors, 96 skipped; 210 Studio tests
passed; 38 website tests and 62 storefront gateway tests passed. Website type
checking/build, repository security, whitespace, model-free update validation
and hosted model catalogue readiness passed. Exact installer extraction, ZIP
and installer JAR identity, model exclusion and isolated welcome-window startup
passed. SmartStudio contains the identical final core JAR. The release source
manifest records 1093 unchanged inputs.

The storefront gateway was deployed using the existing live registered origin
and secret binding. The repository template with an empty origin list was
correctly blocked; its protected deployment copy retained the registered live
origin. Main site and shop returned HTTP 200 after deployment. The updated
server website bundle is delivered with this application update.

Update the server before registers and Studio clients. Publication does not
install or restart live applications. Installed update/rollback, live database
migration, services, printers, drawers and NFC remain separate checks. No macOS
artifact or model weights were published.
