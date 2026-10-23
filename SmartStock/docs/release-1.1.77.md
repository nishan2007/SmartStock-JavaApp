# SmartStock 1.1.77 Windows quick update

Published 22 October 2026. Build 101077. Includes all newly saved changes captured
from the shared workspace: Orders barcode lookup and branding, customer online
accounts and reviews, custom item categories, company expense transfers, stock
ledger improvements, and the updated desktop and storefront bundle. Other
worktrees are historical release checkouts and were not merged.

The build used an isolated byte-preserving source copy. Captured sources remained
unchanged through packaging. Validation passed: 1,273 Java tests, zero failures
or errors, 114 skipped; 92 web/gateway tests; repository security check; working
and staged whitespace checks; model-free archive and hosted catalogue checks;
exact ZIP/JAR identity; isolated native launcher startup using a copied runtime.
The missing local pinned publisher dependency was restored before verification.

The publisher downloaded the entire hosted ZIP and verified its size and SHA-256
before publishing metadata, then checked the metadata response against the artifact.

- Artifact: `smartstock-windows-1.1.77.zip`, 167676613 bytes.
- SHA-256: `dc371ddc001a1b3302f38342fd7714cac676ba497063b4027638ed9c9f147f03`.
- Object: `updates/windows/1.1.77/dc371ddc001a1b3302f38342fd7714cac676ba497063b4027638ed9c9f147f03/smartstock-windows-1.1.77.zip`.

This publication is an update ZIP only. No hosted Worker deployment, live store
schema migration, customer-feature enablement, first-install selection, installed
application or running service was changed. The new local schema resources are
included in the application. Customer-account rollout requires compatible gateway
and store deployments under the existing feature controls.

Update the store server before registers. Live database, Windows service,
installed upgrade/rollback, printer, drawer, NFC, real-phone Orders and customer
email/signup/reset/failover checks remain outstanding. macOS was not built.

Evidence remains outside Git in `SmartStock/target/release-1.1.77-*` and
`SmartStock/target/release-windows-1.1.77`.
