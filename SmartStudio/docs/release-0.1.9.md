# SmartStudio 0.1.9 Windows

Build 1009 includes current image transfer, clipboard, badge/template workspace
and processing changes and uses SmartStock client 1.1.21.

All 28 Maven tests pass. The native packaged launcher and the launcher extracted
from the exact installer both passed startup checks with an isolated user home.
The extracted SmartStudio JAR matches the Maven artifact. No ONNX model weights
are included in the application package.

Installer: `SmartStudio-setup-0.1.9.exe`, 153875135 bytes.
SHA-256: `20cf91b535791124fc2395235fec9e5814b437fd02c1f86f239d0f4ac578ebc5`.

The hosted installer was fully downloaded and verified against its exact local
size and SHA-256 before release metadata was published. The release row was read
back and matched version, build, platform, object path, size, hash and published
status. The installer portal was deployed with the new verified Studio selection;
public HEAD returned HTTP 200, the 0.1.9 filename and 153875135 bytes.

Download: https://downloads.deckers.gy/download/smartstudio/windows

Actual WhatsApp/browser image dragging and pasting, the setup wizard, upgrading a
live installation and macOS remain manual checks. No live Studio installation
was changed.
