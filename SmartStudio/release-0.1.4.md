# SmartStudio 0.1.4 — published October 2, 2026

Windows build 1004 fixes the native drag-and-drop lifecycle error and reopens
SmartStudio after an in-app update. Only SmartStudio changes are included;
the existing released SmartStock 1.0.236 client dependency is retained.

Installer: `target/SmartStudio-setup-0.1.4.exe`

Size: 137632645 bytes

SHA-256: `dc0d918850b983fad14b7dc3a67f8c7ea1c27c7ca527568bf33b7ac0c7c9a8f1`

The uploaded installer was downloaded in full and matched its size and hash
before publishing. Independent update-feed readback verified version, build,
size, and hash. All 13 SmartStudio tests, repository security check, and diff
check passed. Regression tests cover file/image transfer data expiring before
background decoding.

Temporary installed-app checks verified PNG preview loading and automatic
reopening with both the explicit new reopen flag and the old 0.1.3 updater's
silent-install arguments. Temporary installations were uninstalled afterward.
The live paired staff update and actual file dragging on another computer
remain deployment checks. Staff sign in again after the app reopens.
