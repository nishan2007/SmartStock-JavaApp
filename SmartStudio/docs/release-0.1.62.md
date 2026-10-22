# SmartStudio 0.1.62

Windows build 1062; identical SmartStock 1.1.69 client JAR to the core release.
Published 21 October 2026. The staged installer was downloaded in full and its
size/SHA-256 verified before release metadata and public selection changed.
Independent metadata verification and a complete public download also match.

Bill Books now offers Full template + numbers and Numbers only output for
preprinted books. Number columns are configurable; separate movable merge
fields, number-series generation and Excel sources use the existing editor.
Preview, Print, PDF and Riso follow the chosen mode. Saving retains the entire
template. Missing number fields or records stop numbers-only output.

Damaged near-black and missing Publisher table exports are recovered from the
retained PDF at table geometry, keeping separate editable cells. Numbering/NUM
variants are excluded from active bill book discovery and future transfers.
The local cleanup repaired 295 distinct designs and archived 137 numbering
variants, leaving 324 active templates. Customer originals, templates and merge
records are not application payload; publishing does not upload them.

Installer: SmartStudio-setup-0.1.62.exe
Size: 190027647 bytes
SHA-256: 662fcf362a0b75eaba022eb3d47d5562715024f5f32ce6809ed4de727bf7b401

Validation: all 210 Studio tests passed. Exact installer extraction, native
launcher startup, JAR identity, feature-class presence and model/customer/secret
exclusion passed. Repository security and whitespace checks passed. Release
source hashes cover 1093 unchanged inputs across both applications.

Update the server to 1.1.69 before clients. Live installed upgrade/rollback,
authenticated template migration, other-computer visibility, Publisher font and
layout fidelity, physical printing and preprinted-paper alignment remain
separate checks. No live installation or macOS package was changed.
