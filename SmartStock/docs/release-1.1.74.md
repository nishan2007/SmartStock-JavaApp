# SmartStock 1.1.74 Windows release

Prepared 22 October 2026. Build 101074. Includes the current shared workspace changes across completed chats, including the approved-photo filter and shared sheet sequencing engine. Prior features and migrations are retained.

Full gates: 1,236 SmartStock tests, zero failures/errors, 111 skipped; 236 SmartStudio tests, zero failures/errors/skips; 62 storefront Worker tests passed. Repository security and whitespace checks passed. Release input hashes remained unchanged through packaging. Model weights excluded; ONNX runtime and notices retained; hosted model catalogue verified.

Exact update and extracted installer JAR identity, model exclusion and isolated native launcher startup passed. SmartStudio passport sheet and toolbar were rendered from the exact extracted JARs and visually reviewed.

No live installations or services were updated or restarted. Physical printer, cash drawer, NFC, live database migration, upgrade/rollback and real paired-phone upload checks remain outstanding. macOS was not built or published.

Update ZIP: 167458136 bytes; SHA-256 `1b1db61e3cd1f747c735162289953df1511f2fe547d2335698ad3fb3b2db0c7e`.
Installer: 580514868 bytes; SHA-256 `48fda43a4ff7071ef040c975a43f3a5b8459c992a7391b3e3441ca7015126392`.

Evidence: `SmartStock/target/release-windows-1.1.74` and `SmartStock/target/release-1.1.74-*`. Update metadata independently verified against exact size, hash, build, version and object path after the publisher verified the entire hosted ZIP. The full hosted installer was downloaded and size/SHA-256 verified before the public Windows selection changed. Publication completed successfully. Independent public-download verification is recorded alongside the release artifacts.
