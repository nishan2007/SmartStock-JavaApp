# SmartStudio 0.1.3 — published October 2, 2026

Windows build 1003 adds original-preview file/image drops and Copy result.
Uses the existing released SmartStock 1.0.236 client dependency; no SmartStock
server or custom-order spoils changes were packaged with this Studio release.

Installer: `target/SmartStudio-setup-0.1.3.exe`

Size: 137631699 bytes

SHA-256: `ac6dde65111e35f4a922ba19cd81d07afa850efb953a7f2e631693c74a183bcb`

The installer was uploaded, downloaded in full, and verified against its exact
size and SHA-256 before publishing the SmartStudio update-feed entry.
Independent feed readback verified version, build, size, and SHA-256.

Validation: all 11 SmartStudio tests passed, repository security check and
git diff check passed, packaged launcher startup passed, and a temporary
installed-app fixture loaded a PNG and rendered the updated controls. The
fixture was uninstalled afterward. Clipboard PNG tests verified dimensions and
alpha preservation. Actual drag gestures and pasting into external applications
on a staff computer remain live checks; transparency support depends on the
receiving application.

Existing SmartStudio users can sign in and use **Check for updates**.
