# SmartStudio 0.1.64

Windows build 1064, published 22 October 2026. Uses the accepted SmartStock
1.1.72 client JAR; update the store server before clients.

Sheet setup exposes independent left, right, top and bottom margins in
millimetres for binding space. Preview, printing and PDF export use the same
settings. Bill-book sheet restoration preserves all four edges.

All 233 Studio tests passed, with no failures, errors or skips. Repository
security and whitespace checks passed. Standard Windows packaging produced
the installer and portable ZIP. Exact installer extraction, application/core
JAR identity, four margin controls, model-free content and isolated native
launcher startup passed. The launch preview was visually checked. Hosted model
catalogue readiness passed; no model weights were changed or published.

Installer: `SmartStudio-setup-0.1.64.exe`
Size: 190256381 bytes
SHA-256: `38e129fe8372cff355ac8ff93ab587f64bc91bc31c513304997f98e817f9465c`

Portable ZIP: `SmartStudio-0.1.64-windows-20261022-114117.zip`
Size: 192648443 bytes
SHA-256: `dff354637b9ebd32fd0414a8d2e8c9b0430131c0b1138f799c52ad9387cc59f7`

The publisher downloaded and verified the full hosted installer before creating
release metadata. Independent metadata readback matched version, build,
platform, publication status, object path, size and SHA-256.
The protected portal configuration now selects this version. An independent
full download from https://downloads.deckers.gy/download/smartstudio/windows
matched the exact installer size and SHA-256. Portal deployment version:
`78c17d26-8598-4ab1-9aa2-d0eeb365d32a`.

Evidence is retained under `SmartStudio/target/release-0.1.64-*` and
`SmartStudio/target/acceptance-published-0.1.64`.

Live installation/upgrade/rollback and physical printer/binding results remain
outstanding. No running application or service was installed or restarted.
No macOS package was built.
