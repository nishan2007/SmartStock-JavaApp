# SmartStock 1.1.95 combined Windows quick update

Build 101095, prepared 28 October 2026. Frozen shared source and evidence:
D:/SmartStock-combined-1.1.95. Includes all current shared application files.

Customer Accounts requires a charge description, persists it in the transaction
note, and validates it server-side. Customer Type is a list-only dropdown for
account creation/editing, preserving existing saved types. Includes Receiving
Inventory web app and current shared Orders, photo, drawer and service changes.
Hosted Worker deployments remain separate.

- ZIP: smartstock-windows-1.1.95.zip, 168,605,870 bytes.
- SHA-256: 9080472127306977a04f3dc9206518e7c7f0dc7daa07df4763bf37cd71fefdfb.
- Migration bridge SHA-256: 1d173f3c1a01a9cc9451337fb08ded4b3ab0392187a272b71c95eec3acc43fa2.
- Maven: 1,402 tests, zero failures/errors, 151 skipped.
- Web/Worker: 146 passed; website: 70 passed; TypeScript passed.
- Security and whitespace checks passed; model-free archive and hosted model
  catalogue verification passed. Models are unchanged and remain separate.
- Isolated exact ZIP update, rollback, reapply, profile preservation, installed
  JAR/runtime/resources and native launch passed.

Quick-update feed only; no live installation, first-install selection or macOS
artifact. Live database/service, paired account workflows, printers, drawer and
NFC remain rollout checks. Update the server first, then registers.

Published 28 October 2026 after complete hosted download, size and SHA-256 verification. Independent metadata read-back confirmed version/build, platform, published status, exact object path, size and hash.
