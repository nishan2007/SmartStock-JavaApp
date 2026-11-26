# Deckers workspace deployment 1.1.128 — 2026-11-20

SmartStock 1.1.128/build 101128 is installed on POS-SERVER. The six existing staff
gateways are deployed, and the main entry point is https://timeclock.deckers.gy/.
All individual app addresses remain available pending the user's phone acceptance.
Do not remove or redirect those entry points until that acceptance is confirmed.

The package uses the verified frozen 1.1.127 baseline plus the Deckers workspace
changes. Other newer working-tree changes are excluded. Frozen source, its overlay
manifest, artifact, rollback versions, test reports and private deployment evidence
are outside Git at `D:/SmartStock-release-builds/1.1.128`.

- Exact Windows updater ZIP: `smartstock-windows-1.1.128.zip`
- Bytes: 169704112
- SHA-256: `a7577dff5a44b017e224beb5bff1fff52beb2ab48b6b9583973aee2900f13125`
- Installed desktop and service JAR SHA-256:
  `a2d933712ce3704f2452f58f09df42f58abe75228eb7b603ce91516ec06b9aa6`

This version is reserved by this deployment. Retain these exact bytes; use a new
version for later builds. No release-feed metadata, first-install selection or AI
model publication was changed. Version availability was checked read-only before
deployment. The package and hosted model catalogue passed verification.

## Acceptance and installation

The standard Windows update-only packager ran 1564 Java cases with zero failures
or errors and 199 optional skips. All 59 selected gateway and iPhone-reconnection
tests passed. Security and whitespace checks passed. The exact ZIP applied over
an isolated installed 1.1.127 copy. JAR identity, rollback backup, native launcher
configuration/startup, bundled runtime, persistent-model sentinel and profile
sentinel were verified.

POS-SERVER received the complete ZIP and independently verified its size and
SHA-256 before applying the standard SmartStock updater. Both installed and
background-service JARs match the build. The updater verified HTTPS health,
installed version, local schema and LAN discovery. The server task is running.
The application rollback is retained in the server user's SmartStock rollback
directory. Runtime hashes and the encrypted server cloud credential were retained.
Server startup rewrote database.properties using its normal configuration save;
the active mode remains SERVER and store remains 1.

Gateway deployments preserve the existing production variables, service bindings,
domains and legacy routes. Previous active versions were recorded before rollout.
New versions:

- Orders: `1c66be74-eae9-4c6d-9b9a-291f6bef734e`
- Inventory: `4e10b32a-3b7b-42ec-9e20-399a31469ae4`
- Customer Accounts: `2ae4b0e1-72b2-435e-b04c-595d2901300f`
- Balance Sheet: `cfa1639b-94a3-44ef-9096-f0437095d458`
- Overseas: `922428d6-7bdc-4670-ae4b-214cd6734513`
- Time Clock / Deckers entry: `d9d92222-869a-4921-8638-e9fc8aa2472d`

All 36 live public checks passed: root entry, exact shared asset/icon hashes,
employee pages, legacy sites, absent-device and foreign-origin rejection, and
rejection of public LAN-proof verification. Browser inspection confirmed the
live Deckers branding and existing device approval/reconnection flow. No employee
credentials were entered or live inventory/customer transactions created.

## User phone acceptance

Open the Deckers URL in Safari and add it to the iPhone home screen. An existing
paired phone can select **Use existing iPhone pairing** when its new home-screen
storage needs connecting to the existing device record.

Confirm the available apps match the employee role and this device's Mobile
Management grants. Test Add Item and Receiving Inventory on store Wi-Fi, then
switch to mobile data and confirm the store-network message. Retiring separate
app entry points is deferred until the user confirms this test.

Real iPhone authentication, camera/scanning, installed certificate/local-network
permission and physical printer/drawer/NFC behavior are separate acceptance checks.
No macOS or register installation acceptance is claimed by this server rollout.
