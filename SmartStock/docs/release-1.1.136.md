# Deckers lavender workspace — 1.1.136

Windows update-only release of the approved home-screen design: purple header,
pale lavender tiles, orange accents and fourteen purpose-specific raster icons.
The original Deckers logo and employee photo/initials behavior remain. Search,
app ordering, device/employee permission intersection and local-network badges
are retained. Connection status uses the existing authenticated LAN proof.

Prepared from frozen published 1.1.135 plus only workspace HTML/CSS/JS, manifest,
icon resources, server icon mapping and its resource tests. All previous account
and device-specific sign-in changes remain. No schema or hosted gateway change
is required. Visual QA and synthetic browser evidence are in `design-qa.md` and
`output/deckers-home-lavender`; release evidence is outside Git under
`D:/SmartStock-release-builds/1.1.136`.

This publication creates no full installer and performs no installation, restart,
first-install selection, model publication or live data mutation. Install the
update on the store server to serve the new design at deckersapps.deckers.gy.
Live iPhone and installed-server acceptance remain separate; physical hardware
and macOS behavior are not proved by Windows packaging tests.

## Validation

The standard package was built with targeted workspace, employee-session and
account regression tests: 22 cases, zero failures/errors, one optional database
skip. All 43 selected web tests passed. Security and whitespace checks passed.
The preceding 1.1.135 baseline had passed its full 1,585-case Java suite; this
visual-only update did not rerun that entire suite. Browser design/interaction
checks and comparison evidence were retained from the approved implementation.

Exact artifact checks verified the packaged JAR equals the tested JAR and the
final workspace class, HTML/CSS/JS, manifest and all 14 optimized PNG icons match
their final source/classes. Model-free ZIP and hosted model catalogue checks
passed.

- ZIP bytes: 170367381
- ZIP SHA-256: 0fbbfa05dc85a7737d4b4b937a8405c7ff18587c27dd48ab158e7bebfd48d641
- JAR SHA-256: d6ac68512263b6b00c8647a024f1d601de2d75b96d421d77422e33ec8e6e7e63

Published Windows 1.1.136/build 101136 after downloading the complete stored
object and verifying its size and SHA-256. Release metadata was independently
verified against those final bytes. No live application was installed or
restarted; no first-install package was selected.
