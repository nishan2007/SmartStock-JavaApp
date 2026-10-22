# SmartStudio 0.1.63

Windows build 1063, published 22 October 2026. Uses the SmartStock 1.1.71 client
dependency; update the store server before clients.

Includes shared administrator-managed font storage, verified client downloads,
offline font retention and the latest editable Publisher/bill-book template work.
Fonts, customer documents and templates are imported separately, not bundled.

All 228 Studio tests passed. Exact installer extraction, application/dependency
JAR identity, feature classes and native launcher startup passed. The publisher
downloaded the full hosted installer and verified size/SHA-256 before release
metadata creation. Metadata readback and an independent full public download
both matched. The protected portal configuration now selects this version.

Installer: SmartStudio-setup-0.1.63.exe
Size: 190246998 bytes
SHA-256: e4a045bbf152fe821601be405fbd3e6f2eba4a368f645ba14673655fcd9d7c2d
Public URL: https://downloads.deckers.gy/download/smartstudio/windows

Live installation/rollback, authenticated shared-font transfer between computers,
font licensing/fidelity, template appearance and physical printing remain separate
checks. No live installation or macOS build was performed.
