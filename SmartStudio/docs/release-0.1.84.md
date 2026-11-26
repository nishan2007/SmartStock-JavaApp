# SmartStudio 0.1.84 Windows update

Build 1084, prepared 27 October 2026. Keeps business-card dimensions and sheet
capacity unchanged while minimally repositioning layouts inside reported printer
hardware margins when they fit. Retains the multi-page navigation fix.

- Updater: `SmartStudio-update-0.1.84.exe`, 173,039,302 bytes.
- SHA-256: `4e461f065e8cb6a7252568bee8695743f25ea3021389eadb4bf6dd95811b1904`.
- SmartStock dependency: exact verified 1.1.91 release JAR.
- Maven test/package passed: 323 tests, zero failures/errors, one skipped.
- Repository security and whitespace checks passed.
- Packaged native startup and isolated 0.1.83-to-0.1.84 update passed.
- Installed JAR identities match; old Studio JAR removed and Java runtime preserved.
- Extracted updater excludes runtime and model weights and retains inference runtime
  and model notices/licenses.

Acceptance evidence is in `D:/SmartStock-studio-0.1.84`.
Published to the Studio Windows update feed after complete hosted download,
size and SHA-256 verification on 27 October 2026.
No live store installation or macOS build was performed. Physical C5240 printing,
other hardware and live paired store workflows remain separate verification steps.
