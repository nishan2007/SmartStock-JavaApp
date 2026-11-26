# Windows quick updates — 2026-11-12

Published SmartStock 1.1.113 (build 101113) and SmartStudio 0.1.101 (build 1101) from the current shared workspace, including the order exchange and selected-payment reversal work, the Studio banner printing fix and the other current application resources. Studio uses the 1.1.113 SmartStock client.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| smartstock-windows-1.1.113.zip | 169102133 | 97a9f40c528b6fba86d01db76539eb5f4ae96a8677ccbcfd7ca73ef2ccd1d60f |
| SmartStudio-update-0.1.101.exe | 173658161 | b117bfeee57b60bb60e8c4a1b9f3723ff76464103ee2ac8e99aa675d249dfe42 |

Each artifact was fully uploaded and downloaded through the protected publisher; exact size and SHA-256 were verified before creating release metadata. Published release rows were read back and matched artifact path, version, build, size, hash and published state. Existing published artifacts were not overwritten. These are updates for existing installations; no full installer was published.

Validation:
- SmartStock Maven package gate: 1480 tests, zero failures/errors, 160 optional tests skipped. The exchange feature also passed its separately enabled disposable PostgreSQL tests before release preparation.
- SmartStudio Maven package gate: 377 tests, zero failures/errors, one optional test skipped.
- 141 gateway/mobile/portal tests and 79 storefront tests passed; storefront TypeScript/Vite build passed.
- Repository security and whitespace checks passed.
- SmartStock ZIP verified model-free, with hosted immutable model catalogue readiness. Exact updater installed over isolated 1.1.112; installed JAR, native welcome startup, launcher update and runtime/profile preservation passed.
- SmartStudio exact updater installed over isolated 0.1.99; installed Studio/client JARs, native launch, runtime preservation, model exclusion and licence retention passed. File associations, preview registrations and existing shortcuts were restored and verified.

Hosted storefront, Orders, Timeclock and installer portal Workers were deployed with current sources. The storefront retained the registered production origin, secret bindings and frontend asset overlay; production preflight passed. Public pages returned HTTP 200, and downloaded storefront JavaScript/CSS matched the new local bundle byte for byte.

The live desktop applications and store service were not installed, restarted or changed by this publication. Update the store server before registers. Live database/service acceptance, actual inventory exchanges and refunds, physical printing/cash drawer/NFC/camera/cutter checks and macOS packaging remain separate outstanding checks.
