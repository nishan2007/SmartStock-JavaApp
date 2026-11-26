# SmartStock 1.1.106 Windows release

Prepared 5 November 2026, build 101106. Includes the current accumulated SmartStock source, Phone Minutes cash/credit accounting and account-receipt integration, customer statement corrections, shared publication table rendering, and current server/client workflows. SmartStudio 0.1.98 is packaged separately against the identical final core JAR.

## Exact artifacts

- Update ZIP: 168833300 bytes; SHA-256 ed9d054ab4653452733e4dfa16f51ad348fd626d4a8b42baebdfcc50c1a438af.
- All-in-one Windows installer: 581897144 bytes; SHA-256 91b745c1a343a73470b1cad7f80bc51c09c21f2054ba268ab93f4b6212facfc4.
- Model migration bridge: 3318 bytes; SHA-256 0fada6f30ad7de96888dff866f7b1d0b1476272a21b4bb4db13db1c532bea3be.

## Validation

Full SmartStock and SmartStudio Maven gates passed, with 1459 and 353 tests recorded respectively (157 and 1 skipped). The Phone Minutes PostgreSQL integration test uses an isolated temporary cluster and covers balances, mixed/partial payments, released credit, competing payments, duplicate-request storage, receipts, rollback, permissions and cash totals. Screen and statement previews were rendered and inspected. Security and whitespace checks passed. All 1247 recorded release source inputs remained unchanged during packaging.

Model-free package and hosted catalogue validation passed. The exact ZIP was applied over an isolated 1.1.105 installation: installed JAR identity, launcher refresh, native startup, runtime/profile preservation and model exclusions passed. The exact full installer was extracted and its JAR identity and native startup verified. No live installed application or service was changed.

The storefront gateway camera update passed 85 tests and deployed as version 30e13b4b-9611-44aa-859b-d7c738ec5ccf. Existing origins, secret binding, browse-only mode and static assets were preserved. Home, shop and photo host returned HTTP 200; the photo page contains the new camera and brightness controls.

## Rollout

Update the store server before registers and Studio clients. The local minutes migration is included. Apply the additive cloud snapshot-reader/permission migration before relying on cross-store minutes balances; cloud database migration and live store installation are separate rollout steps. Physical iPhone capture and colour/print quality, C5240/receipt printing, drawers, NFC, Windows services, live database acceptance, rollback on deployed installations and macOS packaging remain outstanding. Model weights remain separate.

Local release evidence: SmartStock/target/release-windows-1.1.106; SmartStudio/target/acceptance-0.1.98. Protected publisher configuration and recovery evidence are not Git artifacts.

The SmartStock Windows update was published only after complete R2 download, size and SHA-256 verification. Independent app-release metadata read-back matched version 1.1.106, build 101106, published status, exact artifact path, size and hash. First-install publication is verified separately.

The all-in-one Windows first-install selection was published only after a complete hosted download matched 581897144 bytes and SHA-256 91b745c1a343a73470b1cad7f80bc51c09c21f2054ba268ab93f4b6212facfc4. Public download: https://downloads.deckers.gy/download/windows. SmartStudio 0.1.98 is published in its separate in-app update feed; its full first-install selection remains unchanged.

Independent public-link verification completed at 2026-11-05T14:29:48.304Z: HTTP 200, 581897144 bytes, matching SHA-256 91b745c1a343a73470b1cad7f80bc51c09c21f2054ba268ab93f4b6212facfc4.
