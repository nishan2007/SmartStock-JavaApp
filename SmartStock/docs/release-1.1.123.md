# SmartStock Windows 1.1.123 — 2026-11-17

Cumulative release of the current shared application/server resources, including
signed inventory merges, quotation custom-item search, customer classification,
per-store custom inventory, orders/work queue, receipts and embedded web changes.
SmartDrive quick update was excluded. Existing published versions are immutable.

Validation: full Maven install/test gate passed: 1536 tests, zero failures/errors,
189 optional skips. Website: 81 tests and TypeScript/Vite build passed. Worker
suites: 128 tests passed. Embedded web: 30 tests passed. Repository security and
whitespace checks passed. Packaging reused the completed test gate.

Exact update applied over isolated 1.1.119 with service/relaunch disabled. Installed
JAR identity, launcher refresh, native startup, runtime/profile preservation and
model exclusion passed. Exact first-install EXE payload was extracted separately;
JAR identity, model exclusion and isolated native startup passed. No installer
registry/service actions were exercised. Hosted model catalogue readiness passed;
no model weights were changed or included.

Published artifacts under `D:/SmartStock-release-builds/1.1.123`:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| smartstock-windows-1.1.123.zip | 169330104 | 0a55ded2e53e5060ff7f361653db99c4d8e9d414e8451129af6b5616ff4b1160 |
| smartstock-windows-setup-1.1.123.exe | 582392991 | 05199ceab41a84f5f03e9787ed7284532ce9b1b53b6d71ebc69b10584bfdee2d |

Build 101123. Protected publishers downloaded complete hosted artifacts and
verified size/SHA-256 before publishing metadata or selecting the installer.
Independent release-row verification matched version, build, bucket, immutable
object path, size, SHA-256 and published=true. Public installer manifest matched.

Hosted Workers deployed with production variables retained:

| Worker | Version |
| --- | --- |
| storefront | a7647244-c0c2-40a1-a7c9-8c6d5c7d1839 |
| orders | 0c46bc3b-54b3-4c8b-a944-7472207bd2be |
| timeclock | b43c1c5b-afed-4674-8830-192bc8d0515c |
| customers | 356668cd-f8db-4674-b5b0-2c88c5e30664 |
| inventory | c9f31393-19b7-43b8-b490-f3b70e7ba841 |
| balance sheet | 94f54190-3617-4f18-a6d3-16f997e59eb6 |
| installer portal, final Studio selection | 28986941-ec8d-47b9-a97c-415cda8323fa |

Storefront serves the exact rebuilt JS/CSS; hosted hashes matched. Orders,
timeclock, customers, inventory and balance-sheet roots returned login redirects;
photo and download roots returned HTTP 200. These probes do not prove authenticated
workflows against the installed store server.

Update store servers before registers. No live installation/service restart or
live database migration was performed. Live server/register database workflows,
Windows installer/service registration, real model downloads/offline inference,
physical printer/drawer/NFC behavior and macOS packaging remain outstanding.
