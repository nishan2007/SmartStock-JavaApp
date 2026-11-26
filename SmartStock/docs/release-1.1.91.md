# SmartStock 1.1.91 Windows quick update

Build 101091, prepared 27 October 2026 from the shared source frozen at
D:/SmartStock-combined-1.1.91/source. All current SmartStock application changes
are included. SmartStudio 0.1.83 uses this exact shared client.

Adds copying order attachment files from the Orders panel and file manager,
with Ctrl+C, actual clipboard files and preserved safe filenames. Includes current
Orders controls, production alignment, due dates, multi-size customer orders,
customer-save retry fixes, balance-sheet web access and other shared changes.
Hosted Worker deployments are separate from these Windows quick updates.

- smartstock-windows-1.1.91.zip: 168,570,503 bytes.
- SHA-256: 81638aef66180f351ba4a105367209b92a3723e074f720539e34945399906f18.
- Maven: 1,384 tests, zero failures/errors, 146 skipped.
- Web/gateway: 111 passed. Website: 70 passed. TypeScript passed.
- Security and whitespace checks passed; frozen-source comparison found no changes.
- Model-free archive and hosted catalogue checks passed.
- Isolated update, rollback, reapply, persistent-profile preservation, exact
  installed JAR identity, required resources, bundled runtime and launcher passed.

Evidence, hashes and validation.json: D:/SmartStock-combined-1.1.91/.
No live store installation, full installer or macOS build. Live database/service,
clipboard paste into target Windows apps, paired workflows, printer, drawer and
NFC checks remain separate. Update the server first, then registers and Studio.
Published 27 October 2026 after complete hosted download, size and SHA-256
verification. Independent update-feed metadata verification passed for version,
build, platform, published status, object path, size and hash.
