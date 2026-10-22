# SmartStudio 0.1.47 Windows publication

Published 19 October 2026, build 1047. Uses SmartStock 1.1.52 client libraries.

Includes layer forward/backward/front/back controls with multiple selection, undo/redo, context menus and shortcuts; Publisher field-frame/artwork recovery; and PowerPoint master-placeholder import correction. MASALA MAN shared archives were repaired separately from the application release.

Validation: 160 Studio tests passed; security check and git diff --check passed. Standard Windows packaging script produced the portable ZIP and installer. Exact installer extraction, JAR identity, model-free content and native launcher startup passed. Hosted model catalogue check passed. Authenticated upload was fully downloaded and verified before metadata publication; published metadata and public selection were verified. Public installer was independently downloaded and checked again.

Installer: SmartStudio-setup-0.1.47.exe
Size: 189653816 bytes
SHA-256: 1ecc07d2e5a88f3672e3dc2f497d64ec98d580f57123aac81a02e62aa632725a
Public download: https://downloads.deckers.gy/download/smartstudio/windows

Portable ZIP: SmartStudio-0.1.47-windows-20261019-111959.zip
Size: 192039784 bytes
SHA-256: 2a83a035c280091e4c7e89e91578ae52a8f0390e95efbb16cc31a27c248d0e92

Publication does not install or restart the user's running application. Installed upgrade behavior, physical label printing and scanner behavior remain outstanding. No macOS artifact was built. Logs are under SmartStudio/target/release-0.1.47-*.
