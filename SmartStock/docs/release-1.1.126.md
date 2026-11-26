# SmartStock Windows 1.1.126 — 2026-11-19

Build 101126 is the cumulative quick update of the completed shared application
changes. It adds three customer phone numbers with individual optional contact
types and shared Mobile Management pairing, per-app access and revocation.
It retains the Customers workspace/history, product-ad comparison, overseas and
mobile startup changes, and SmartStudio server-access fixes from recent chats.
SmartStudio 0.1.105 has no newer source changes and is not republished. The
previously deferred standalone SmartDrive release is not included.

Exact update-only artifact:

- Path: `D:/SmartStock-release-builds/1.1.126/smartstock-windows-1.1.126.zip`
- Bytes: 169394196
- SHA-256: `845552a733f2c9893dbe7ffcb488f09f7f1b56d7633f3b79c807d380a7d25e8f`

The standard Windows packager built from a frozen 2811-file application/source
snapshot, supplemented with sibling SmartDrive test support. All source hashes
were checked against the shared working tree. The initial snapshot test run
found a missing sibling IdentityStream.cs fixture; after adding the fixture,
all four affected tests passed. The combined reports contain 1556 test cases,
zero failures/errors and 196 optional skips. Packaging then reused those test
results. All 77 selected web/gateway tests, security and whitespace checks passed.

The model-free ZIP and hosted model catalogue readiness checks passed. No model
weights were changed, packaged or published. The exact ZIP applied over isolated
1.1.124. Installed JAR identity, previous-version backup, native welcome-screen
startup, launcher refresh, runtime preservation and model/profile sentinels
passed. An inherited test-only launcher home was corrected to the new isolated
home before the final native startup check. No live application files or services
were changed.

Published through the standard protected Windows/R2 publisher. The complete hosted
ZIP was downloaded and its size/SHA-256 matched before inserting release metadata.
Independent read-back confirmed Windows version 1.1.126, build 101126, published=true,
content-addressed object path, 169394196 bytes and the exact SHA-256 above.

All six required staff gateways were deployed with their production public
variables retained and their prior versions recorded for rollback:

| Gateway | Deployed version |
| --- | --- |
| orders | 8a767a7d-de22-4f61-b031-a5f76aded8e9 |
| inventory | 7f3589d9-72c8-482e-bbd8-8c036ddb07d4 |
| customers | e5466d80-d3dc-4c64-8c8d-5351fdbce83a |
| balance-sheet | 52af0d95-eb54-4b7a-9032-17c8bd54cae2 |
| overseas | e87e63a0-3e01-4098-a84e-6ddf53333b19 |
| timeclock | 3d9371ad-fd52-4bb6-82d4-97153ebf444b |

Public checks passed for all six root redirects and exact hosted asset hashes.
The five dedicated staff gateways also passed mobile method/origin rejection
checks. The shared launcher currently returns HTTP 404 through every origin
because the installed store server has not received this Java release. The
gateway publication is complete; shared pairing is not usable until server
rollout. No authenticated real-phone workflow is claimed verified.

Private release evidence is under D:/SmartStock-release-builds/1.1.126 outside Git.

Update store servers before registers. Existing independently approved phones
must pair again through Mobile Management. Live database upgrades, authenticated
real-phone workflows through every origin, Windows service behavior, physical
printer/drawer/NFC behavior and macOS packaging remain outstanding. First-install
download selection is unchanged; this is a Windows update ZIP release.
