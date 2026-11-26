# Deckers unified screens 1.1.129

Prepared from the frozen 1.1.128 baseline, with only the unified-screen correction
and its regression tests overlaid. Unrelated working-tree changes are excluded.
All separate app entry points are retained until phone acceptance.

This correction keeps all 14 screens inside one Deckers document. One employee
session authorizes them; current device grants and employee permissions are both
required on screen operations. Add Item, Receiving and the other stock workflows
require fresh direct-store-network proof on every request. There are no iframe
screens or tile handoffs in the public workspace. Legacy entry points continue
to use their existing login flows.

The standard model-free Windows update package, exact checksums, frozen source,
Maven reports, gateway dry runs and deployment receipts are held outside Git at
D:/SmartStock-release-builds/1.1.129. Deployment and exact artifact evidence will
be recorded after verification. No release-feed publication is implied.

## Verified deployment

SmartStock 1.1.129/build 101129 is installed on POS-SERVER and its background
service. All six staff gateways were deployed with production variables and
service bindings preserved. The main entry is https://timeclock.deckers.gy/.

- ZIP bytes: 169720619
- ZIP SHA-256: ca24165561df3a9bb2da9b2772638669a72f7b4cdabb759a7d0016b0fa34880e
- Desktop/service JAR SHA-256: 06e58025c93bfab5a0e4de9091f35de9524fe528e40e1acb8a0e343d3718c91e
- Frozen package suite: 1566 tests, 0 failures/errors, 199 skipped.
- Selected JavaScript suite: 60 passed.
- Live public checks: 52 passed, including exact hashes for all 14 generated
  screen modules, shell resources, icons, original app addresses, and rejection
  of unapproved devices, foreign origins and public LAN-proof verification.
- Standard updater acceptance: exact installed JAR, previous-version rollback,
  retained Java runtime and model sentinel all verified. Installed app launched
  under an isolated development home; only that owned smoke process was stopped.
- Server acceptance: exact desktop/service JARs, service Running, and ports
  8443/8444/8445/8446/8450 listening after the normal updater completed health,
  schema/version and LAN discovery checks.

Gateway versions:

- Orders: 37b1216b-7c18-434e-b467-8cc225ce0c65
- Inventory: 38b9182e-b038-4531-a304-f6ce56efd898
- Customers: 8bb3db9d-5baf-407b-8e16-0775a0d9dbfe
- Balance Sheet: 5d1d35f8-23b0-42d5-b9cb-5eebd81254e8
- Overseas: 6e809ca5-5247-4347-a100-307a24c281c2
- Time Clock / Deckers: 692121ac-82c2-4942-bc9d-8a26ce379edc

No app-release feed, first-install selection or model publication was changed.
This deployed version is immutable; retain the verified artifact bytes. Existing
individual app URLs are retained. Fully close and reopen the installed iPhone
Deckers app to load the new shell, then verify one login across the available
screens and stock access on store Wi-Fi versus mobile data.

Real iPhone login/storage, camera/scanning, certificate/local-network permission,
and physical printer/drawer/NFC checks remain separate user acceptance. No Mac
installation or physical hardware success is claimed.
