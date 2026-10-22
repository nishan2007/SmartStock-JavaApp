# SmartStock 1.1.76 Windows quick update

Published 22 October 2026. Build 101076. Includes all newly saved application
changes captured from the shared workspace: the public Orders workspace,
approved-browser pairing, authenticated dashboard/details/assignment actions,
mobile Orders improvements and installable home-screen assets. The Orders Worker
was already deployed. No database migration is required by these changes.

The build used an isolated, byte-preserving copy of the saved sources. All captured
files remained unchanged through packaging. Validation passed: 1,252 Java tests,
zero failures/errors, 112 skipped; 13 Orders web/gateway tests; repository security
check; working and staged whitespace checks; model-free archive and hosted model
catalogue checks; exact ZIP/JAR identity and isolated native launcher startup.

The publisher downloaded the entire hosted ZIP and verified its size and SHA-256
before publishing metadata. The resulting metadata was independently checked.

- Artifact: `smartstock-windows-1.1.76.zip`, 167503791 bytes.
- SHA-256: `2ac89a8ffa6c28ab5cb72b2c5fc2013ed6da5055b1674466088fa408d74d8075`.
- Object: `updates/windows/1.1.76/2ac89a8ffa6c28ab5cb72b2c5fc2013ed6da5055b1674466088fa408d74d8075/smartstock-windows-1.1.76.zip`.

This is an update ZIP only. No first-install selection, model files, installed
applications or running store services were changed. Update the store server
before registers. Live local-database, service, installed-app upgrade/rollback,
physical printer/drawer/NFC and real-iPhone pairing, mobile-data, Orders-write and
revocation checks remain outstanding. macOS was not built or published.

Evidence is under `SmartStock/target/release-1.1.76-*` and
`SmartStock/target/release-windows-1.1.76`, outside Git.
