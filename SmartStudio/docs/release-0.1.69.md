# SmartStudio 0.1.69 Windows update

Build 1069, published 23 October 2026, using the current SmartStock 1.1.80 client dependency.

Includes the purple icon ribbon with labels below actions, dedicated Print Setup tab, saved per-template print setup, empty initial data tables, and saved custom fields and records. Shared-template record saving requires the updated store server; publishing this client does not update the server.

This is an update-only executable for the existing Studio updater. It includes application files and dependencies, excludes the Java runtime and AI model weights, requires an existing installation, and does not create shortcuts or replace uninstall registration. The public first-install selection is unchanged.

Artifact: `SmartStudio-update-0.1.69.exe`, 169,676,397 bytes.
SHA-256: `21b8cb4bdde78f9d0e5199fa403d4de4a32b2887762aad150f773bf32e1b0ff5`.

The publisher downloaded the complete hosted artifact and verified size and SHA-256 before publishing the update feed metadata. Independent metadata verification matched version, build, platform, published status, object path, size and SHA-256. No first-install portal selection was changed.

Validation: Studio tests/package passed; previous full core gate passed (1,287 tests, 115 skipped); security and whitespace checks passed. Hosted model catalogue verified. Extracted Studio/core JARs match build outputs; runtime/model exclusion, retained notices/runtime dependency, native launcher and exact-package ribbon rendering passed. Update applied to an isolated copy of 0.1.68 and the updated native launcher passed startup in an isolated development profile. No live application, store, service or database was installed or restarted. Physical printing, live shared-server workflows, manual DPI/accessibility, and rollback remain separate verification steps.

Local evidence is under `SmartStudio/target/release-0.1.69-*`, `acceptance-0.1.69-update`, and `update-install-0.1.69`. Protected operator configuration remains outside Git.
