# SmartStudio 0.1.50 Windows update

Build 1050, published 19 October 2026, with SmartStock 1.1.55 client libraries.
Adds the Banners workspace, physical custom sizes in FT/IN/CM/MM, bounded scaled
canvas/thumbnail rendering, zoom/pan/Fit, reusable local banner templates, and
actual-size vector PDF output with UserUnit for large-format pages.

Installer: SmartStudio-setup-0.1.50.exe
Size: 189713812 bytes
SHA-256: faabf3ed2270694ff61fbe7f649f7db45358ebad34e53b4dab6d6c7aa81a65e6

Portable ZIP: SmartStudio-0.1.50-windows-20261019-141730.zip
Size: 192097104 bytes
SHA-256: b2e61e4339f9f49e1b0e57c729b4e2c4020f65ef9279afbcb746c72238453d50

Validation: all 173 Studio tests passed. The preceding SmartStock full suite
passed (1142 tests, 90 skipped). Security and whitespace checks passed. Exact
extracted installer JAR identity and native startup passed. Loose files and
all packaged JARs were checked for model weights; none were present. Hosted
verified model catalogue passed. Banner workflow checks include both
orientations of 10 x 100 ft and 30 x 300 ft, archive reopen, PDF dimensions,
photos, gradient bounds, undo/redo and pan with a 512 MiB test heap.

Publication evidence: target/release-0.1.50-publish.log, metadata.json,
public-selection.log and public-download.log. The hosted installer passed full download, size and SHA-256 verification before release metadata was created. Independent metadata matched the accepted bytes; the public installer selection was updated.

Banner templates use the local library and portable .sspub files. PDF readers
and RIPs must honour UserUnit for large pages. Gradients use object rasters
bounded at 2048 pixels per side; source photos retain source detail. See
banners.md for size, archive, export and printing limits.

No running installation was updated or restarted. Installed update/rollback,
store sessions, Windows integration and physical large-format/RIP printing
remain separate acceptance checks. No macOS artifact was produced.
