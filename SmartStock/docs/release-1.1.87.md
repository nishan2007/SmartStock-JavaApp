# SmartStock 1.1.87 Windows quick update

Build 101087, prepared 26 October 2026 from the shared SmartStock working tree.
Source frozen at D:/SmartStock-quick-update-1.1.87/source. No source changes were
found after the freeze during verification. SmartStudio remains a separate release.

Includes cancelled-order exclusion from Current orders, whole-order production
stage selection (including Not started), permission-controlled inline employee
assignment by employee ID, Delivery dropdown actions for all remaining lines,
and the existing-customer order save/retry correction. Includes the other current
shared SmartStock application sources, resources and schema migrations. Separately
hosted Worker deployment is outside this Windows quick update.

- ZIP: smartstock-windows-1.1.87.zip, 168,551,353 bytes.
- SHA-256: 5d426d93b0900f894ea37f66f6052feb345388d41edefa47f13b3a0df833fee4.
- Migration bridge SHA-256: 9c2d70f1b1d007e35527b7130c75c0dede403b65260b4b71d7b1779c0ab5f298.
- Maven: 1,370 tests, zero failures/errors, 146 skipped.
- Frozen web/gateway tests: 111 passed. Website: 69 passed; TypeScript passed.
- Repository security and whitespace checks passed.
- Model-free archive/version/runtime/licences/migration checks and hosted model
  catalogue readiness passed.
- Isolated exact ZIP update, rollback, reapply, persistent-profile preservation,
  required resources, installed JAR identity, bundled Java and native launcher passed.

Evidence, frozen hashes, logs and validation.json: D:/SmartStock-quick-update-1.1.87/.

Quick-update feed only; no permanent installer selection or macOS build. No live
store installation, database/service upgrade or physical hardware acceptance was
performed. Verify signed-in saves/assignments, delivery, migrations, phone access,
printer, drawer and NFC separately. Update the server first, then registers.

Published 26 October 2026 after full hosted download/size/SHA-256 verification.
Independent metadata read-back confirmed version, build, platform, published
status, object path, size and hash. Object: windows/1.1.87/5d426d93b0900f894ea37f66f6052feb345388d41edefa47f13b3a0df833fee4/smartstock-windows-1.1.87.zip.
