# SmartStudio 0.1.77 Windows quick update

Build 1077, 24 October 2026. SmartStock client dependency 1.1.80.

Published to the Windows SmartStudio update channel after complete hosted-artifact
download, size and SHA-256 verification. The first-install selection is unchanged.

Adds Vinyl Cut and Print & Cut label production, custom roll/sheet sizing,
selectable 0.75-inch roller grip edges, loading rotation, frozen jobs, matching
actual-size print/PDF, manual three-target registration, USBPRINT/serial output
and measured pen-square calibration. Uses the existing Studio design components.
See [setup and physical acceptance](print-and-cut-design-and-setup.md).

Artifact: `SmartStudio-update-0.1.77.exe`, **172,273,574 bytes**.
SHA-256: `c33ebd91b3432ac4d784bb0afb69e796f70358f17faff75e4241feada5c2556d`.

Validation: Maven package passed 319 tests, zero failures/errors, one skipped.
Security, whitespace and hosted model catalogue readiness passed. Exact installer
extraction verified application/core JAR identity, new cut classes, serial runtime,
inference runtime and notices, and exclusion of Java and model weights. Native
launcher and ribbon rendering passed. An isolated 0.1.76 → 0.1.77 installation
replaced the application, retained the Java runtime and passed native startup.

This is an update-only artifact. The permanent first-install selection is not
changed. No live store installation, driver change, database change or physical
cutter motion was performed. Printer scale, cutter units/axes, pen alignment,
kiss cutting, long-feed tracking and grip/slip acceptance remain separate physical
checks. Native USB transport and macOS hardware are not physically verified.

Evidence is under `SmartStudio/target/acceptance-0.1.77-update`,
`update-install-0.1.77` and `release-0.1.77-*`. Machine-local settings, publisher
secrets and verification downloads remain outside Git.
