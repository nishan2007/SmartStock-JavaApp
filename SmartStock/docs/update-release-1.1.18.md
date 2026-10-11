# SmartStock 1.1.18

Windows build 101018. Prevents duplicate custom orders from overlapping Save actions and retries after uncertain responses. Retains the original request and idempotency key for retries, blocks clearing an unresolved submission, and clears confirmed orders before printing.

Packaging Maven suite: 906 tests, zero failures/errors, 50 skipped. Repository security and whitespace checks passed. Model-free update package and hosted verified model catalogue checks passed.

Outstanding: live register duplicate-click/lost-response workflow, live database/service behavior, actual upgrade/rollback, independent model install/offline processing, physical printers/cash drawers/NFC. macOS packaging remains outstanding. No running store installation changed. Update the store server first, then registers.

Exact installer extracted and checked: packaged JAR SHA-256 matched the tested Maven build; no ONNX model weights were present. Native launcher opened the welcome screen in an isolated development profile, confirmed by startup log; smoke process stopped. Full installer setup, service installation and live order entry remain unverified.

Updater ZIP: 133335023 bytes; SHA-256 f2f7c4eea597240a67907656dc1ebe2954f9ac670ac6dca824c5595a2081ae84.
Installer: 546682505 bytes; SHA-256 8ea319020f3430e4f34eeb648bfae61ab33fc190066a22e06748a31e48ce4386.
Migration bridge: 3318 bytes; SHA-256 dff42958236d12ed1d8fff9ba366e9648fc21586a3ec7eb632aa3c0e804fca61.

Publication completed: full hosted update and installer downloads matched local size/SHA-256 before update metadata and installer selection changes. Supabase release metadata was read back and matched version 1.1.18, build 101018, Windows platform, exact object path, size, SHA-256, and published status. Public installer HEAD returned HTTP 200, filename smartstock-windows-setup-1.1.18.exe and length 546682505. Download: https://downloads.deckers.gy/download/windows.
