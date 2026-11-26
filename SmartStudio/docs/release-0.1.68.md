# SmartStudio 0.1.68 Windows release

Published 23 October 2026. Build 1068; current SmartStock client dependency 1.1.80.

Includes task-ribbon navigation, grouped Home/Insert actions, selection-specific formatting tabs, compact layouts, bounded settings windows, workspace tiles, alignment/distribution and merge-field insertion. Retains the current PDF editor and specialist photo/printing workflows. The proposed icon-and-label ribbon refinement is not included.

The packaging script now includes the model notices alongside the inference runtime. Model weights remain excluded. A stale locally resolved core dependency was detected before upload; the core was rebuilt/installed locally and SmartStudio rebuilt and tested before the final package. Only the final accepted installer was uploaded.

Validation: SmartStudio package/test run completed with 260 tests, zero failures/errors and one skipped. Prior full SmartStock gate completed with 1,287 tests, zero failures/errors and 115 skipped. Repository security, whitespace and hosted model catalogue checks passed. Final extracted installer JARs match the current Studio/core build outputs. Ribbon, arrangement and PDF classes, model exclusion, inference runtime and notices were verified. Exact extracted native launcher startup and isolated ribbon rendering passed.

Installer: **190,688,434 bytes**. SHA-256:

`047753706b10b1c7ad3d557e1369d863a1a0798f675a7edc069c8f78031f39ed`

The publisher downloaded and verified the complete stored installer before creating release metadata. Independent metadata verification matched version, build, platform, published status, object path, size and SHA-256. The public SmartStudio Windows selection was updated while preserving other portal settings. A separate complete public download matched the accepted installer.

Download: [SmartStudio for Windows](https://downloads.deckers.gy/download/smartstudio/windows).

No live installation, database or service was changed. Physical printing, drawer/NFC, real paired phone/model workflows, manual DPI/accessibility and upgrade/rollback remain separate acceptance checks. macOS was not built or published.

Local evidence: `SmartStudio/target/release-0.1.68-*` and `SmartStudio/target/acceptance-0.1.68-final2`. Protected publisher configuration and profile files are not repository artifacts.
