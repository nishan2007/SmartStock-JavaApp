# SmartStock Windows 1.1.127 — 2026-11-19

Build 101127 fixes existing iPhone home-screen apps failing to recognize the
shared Mobile Management approval. Each isolated app can connect through the
already paired Safari browser without another administrator QR. Connections
retain the same device record, app grants, employee permissions and revocation.

The release uses the frozen 1.1.126 source plus the home-screen fix. It excludes
subsequent unrelated work. Private build and verification evidence is under
`D:/SmartStock-release-builds/1.1.127`, outside Git.

- Update: `smartstock-windows-1.1.127.zip`
- Bytes: 169401621
- SHA-256: `32fb98596aa629f54368e154d794339c1959fd2053e985eb3674e730542eb9df`

The standard Windows packager completed 1558 test cases with zero failures or
errors and 198 optional skips. The focused gateway/browser suite passed all 53
cases. Security and whitespace checks passed. The model-free ZIP and hosted
model catalogue checks passed; no models or first-install selection changed.

The exact updater applied over an isolated 1.1.126 installation. Installed JAR
identity, rollback backup, native welcome-screen startup, runtime preservation,
and model/profile sentinels passed. The acceptance harness was moved outside
the installation being replaced after its original classpath caused a Windows
file lock; the release ZIP was not rebuilt or changed.

The publisher downloaded the uploaded R2 object and verified its full size and
SHA-256 before publishing metadata. Independent read-back confirmed version,
build, Windows platform, published state, artifact size and SHA-256.

All six staff gateways were deployed with current production variables and
bindings preserved: Orders, Inventory, Customer Management, Balance Sheet,
Overseas and Time Clock. Their previous versions were retained for rollback.
Public pages returned 200 and rejected foreign-origin connection requests with
403. The five dedicated asset gateways contain the new reconnect script reference.

## Required store-server step

The active production profile on this computer is a register connected to
POS-SERVER. The server is reachable, but Windows remote administration and its
administrative file share are unavailable from this register. No live installed
application, Windows task, service or store database was modified here.

On **POS-SERVER**, use **Status → Check for Updates** and install **1.1.127**
before updating registers. The app installs the local home-screen-link migration
through its guarded provisioning path. At publication, the public reconnect
script endpoint still returned 405 from the older backend; it must return 200
after POS-SERVER loads the new release. Time Clock's server-rendered HTML also
depends on that backend update.

Then close and reopen each existing iPhone icon, select **Use existing iPhone
pairing**, confirm in the Safari browser where pairing succeeded, and return to
the app. Actual POS-SERVER installation, authenticated iPhone verification and
live database/service behavior remain outstanding. No physical printer, drawer,
NFC or macOS acceptance is claimed.
