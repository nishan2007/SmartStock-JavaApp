# SmartStudio 0.1.59 Windows release

Build 1059, SmartStock 1.1.65 client dependency.
Adds 0–100% image/photo transparency, preserved in saved designs and used by
preview/export/print rendering. Adds background removal to initial and later
photo framing, plus Edit photo / crop / background beside photo replacement.
Removal uses the authenticated store server with its installed Best model and
a bounded processing copy. Framing, position, rotation and corrections survive.

Installer: SmartStudio-setup-0.1.59.exe
Size: 189974266 bytes
SHA-256: 95bf6fb73dbef2223742a7405db608fb4971c4df9485171b71adba26159abeca

All 198 Studio tests passed. Targeted core publication tests, security,
whitespace, exact-installer JAR identity/native startup and model-free checks
passed. Publication evidence: target/release-0.1.59-*.log.
Live server background removal, physical printing and installed upgrade/rollback
remain separate acceptance checks. No running installation was changed and
no macOS package was produced.

Published 21 October 2026. Hosted artifact size/SHA-256 and release metadata
verified before public selection; full public download verified afterward.
Public installer: https://downloads.deckers.gy/download/smartstudio/windows
