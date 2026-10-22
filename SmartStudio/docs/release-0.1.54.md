# SmartStudio 0.1.54 Windows release

Build 1054, prepared 20 October 2026, using SmartStock 1.1.56 client libraries.

Published 20 October 2026. The protected publisher fully downloaded and verified the hosted installer before creating release metadata. Independent metadata verification matched version, build, path, size and SHA-256 before the Windows public selection was updated. A complete download from https://downloads.deckers.gy/download/smartstudio/windows matched the exact installer size and SHA-256 above.

Adds brightness, contrast, saturation and warmth to the shared photo framing dialog, with independent correction reset and background processing. Original pixels and transparency are retained. Passport sheet preview and printing preserve the chosen photo background color instead of replacing it with white. Opaque original backgrounds still require removal before a replacement color can show behind the subject.

All 186 Studio tests passed. Repository security and whitespace checks passed. The exact extracted installer passed JAR identity and native startup checks. A smoke harness using the extracted bundled runtime verified corrections, alpha preservation, blue backgrounds in both passport templates and printable output. Loose-file and 41 embedded JAR checks found no ONNX model weights. Hosted model catalogue readiness passed.

Installer: SmartStudio/target/SmartStudio-setup-0.1.54.exe
Size: 189754874 bytes
SHA-256: 572538a20a0dde7e8057d7b229c2fabfa8750ba6ce05e7e01b8c9fb1beec537b

Portable: SmartStudio/target/SmartStudio-0.1.54-windows-20261020-160612.zip
Size: 192140962 bytes
SHA-256: 4c0739c6eabf0a20ceec63c43888308bacac9fde66b63c18a00911b911738df1

Evidence is in target/release-0.1.54-* logs. Installed upgrade/rollback, interactive editor review and physical printer checks remain outstanding. No live installation was changed. No macOS artifact was built.
