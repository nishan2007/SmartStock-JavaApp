# SmartStock 1.1.17

Windows build 101017. Includes explicit Web Status first-load triggering and visible loading/error recovery, numeric/chronological order-header sorting, and normal-desktop relaunch from the elevated updater.

Full Maven suite: 903 tests, zero failures/errors, 50 skipped. Web Status native Swing success/error/refresh tests passed separately with headless disabled before packaging. Order sorting/selection tests and updater tests passed. Security and whitespace checks passed. Model-free package and hosted verified model catalogue checks passed. Existing Explorer desktop COM interface discovery succeeded (read-only).

Exact installer extracted; packaged JAR SHA-256 matched tested Maven build. Native launcher opened welcome screen using an isolated development profile; smoke process stopped.

Updater ZIP: 133333635 bytes; SHA-256 793ac584c0eff9b3a4ae040fed35145f0a66e7579e674b92b5dd62ad019b4a1f.
Installer: 546681722 bytes; SHA-256 19924c069242b916724995efb8e62e6b12352b3cd3fe9b11560e8bb7fc1df60a.

Outstanding: live store Web Status response, actual elevated-update relaunch/upgrade/rollback, services, database mutations, independent model install/offline processing, physical printer/cash drawer/NFC. User confirmed Explorer attachment drops work after reopening normally; elevated UI token and normal Explorer token were confirmed read-only. No running store installation changed. Update server first, then registers.

Publication completed: complete hosted downloads matched local size/SHA-256 before update metadata and installer selection changes. Public installer HEAD returned HTTP 200, filename 1.1.17 and length 546681722. Download: https://downloads.deckers.gy/download/windows. After this upgrade, reopen normally once because the previously installed updater can still inherit elevation.
