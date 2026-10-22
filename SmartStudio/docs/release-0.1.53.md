# SmartStudio 0.1.53 Windows release

Build 1053, published 19 October 2026, using SmartStock 1.1.56 client libraries.

Passport printing accepts portrait, landscape and reverse-landscape 4 x 6 paper
and shifts photo groups within asymmetric printable areas without scaling.
Only advertised paper sizes are requested from real drivers; forced Letter and
full-sheet margin defaults are removed. Native printer setup/preferences and
capability calls for passport/badge printing run away from the Swing event
thread, with immediate status, duplicate-request exclusion and cancellation
cleanup. Image Studio defaults to GPU preferred, retaining CPU fallback.
Previously published banner stretching, large assets, clipboard/templates and
submission workflows are retained.

All 182 Studio tests passed. The exact extracted installer passed application
and SmartStock dependency identity, native startup, and loose-file/embedded-JAR
model-free checks. A standalone smoke harness using its bundled runtime verified
three paper orientations, asymmetric margins, unchanged passport photo size and
the actual GPU-preferred default. Canon driver latency and physical printing
were not timed or verified by these automated checks.

SmartStudio/target/SmartStudio-setup-0.1.53.exe
Size: 189749285 bytes
SHA-256: 4c8cebadc934179eb67ad62d98abee2b70c80674cedf63d53a60e2eff22144b5

SmartStudio/target/SmartStudio-0.1.53-windows-20261019-190929.zip
Size: 192135439 bytes
SHA-256: 5c8509849d75467ff6c4ccffd8c41c9c62d94a6fcf241d7d471a86c94db4f4ca

The protected publisher fully downloaded and verified the hosted installer
before creating metadata. Independent metadata matched version, build, key,
size and hash before changing the public Windows selection. The full public
download also matched the exact installer size and SHA-256.

Public download: https://downloads.deckers.gy/download/smartstudio/windows
Evidence: target/release-0.1.53-{build,package,acceptance,feature-acceptance,
publish,public-selection,public-download}.log and metadata.json.

Security/whitespace checks and hosted model-catalogue readiness passed. No model
weights are packaged. Running installations are unchanged. Update the server
first. Installed upgrade/rollback, live LAN/queue permissions, phone cameras,
Canon rear-tray paper/type matching and physical printer checks remain pending.
No macOS artifact was built.
