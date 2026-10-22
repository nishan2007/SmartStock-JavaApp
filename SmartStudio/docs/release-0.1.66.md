# SmartStudio 0.1.66 Windows release

Published 22 October 2026. Build 1066; shared SmartStock client 1.1.74. Adds optional 2 mm passport bleed and trim marks while preserving trim dimensions and face framing. Includes current independent margins, template-only printing, position order, cut-and-stack and template repairs.

Full gates: 1,236 SmartStock tests, zero failures/errors, 111 skipped; 236 SmartStudio tests, zero failures/errors/skips; 62 storefront Worker tests passed. Repository security and whitespace checks passed. Release input hashes remained unchanged through packaging. Model weights excluded; ONNX runtime and notices retained; hosted model catalogue verified.

Exact update and extracted installer JAR identity, model exclusion and isolated native launcher startup passed. SmartStudio passport sheet and toolbar were rendered from the exact extracted JARs and visually reviewed.

No live installations or services were updated or restarted. Physical printer, cash drawer, NFC, live database migration, upgrade/rollback and real paired-phone upload checks remain outstanding. macOS was not built or published.

Installer: 190230244 bytes; SHA-256 `275ec229223c5953d275c3f8968907f47e4d062d2537eaf9688a131063b0a03a`.

The complete hosted installer was downloaded and size/SHA-256 verified before release metadata publication. Independent metadata verification passed, the public portal selection was updated while preserving other settings, and a separate full public download matched the exact installer.

Evidence: `SmartStudio/target/release-0.1.66-*`, `SmartStudio/target/acceptance-published-0.1.66-final`.
