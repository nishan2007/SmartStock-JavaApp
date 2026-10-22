# SmartStudio 0.1.48 Windows release

Published 19 October 2026.

Build 1048; uses SmartStock 1.1.53 client libraries. Includes Add text field and
Bind selected text, blank per-person Full name/ID/custom entry fields, details
entry after photo selection, opt-in badge records, admin history loaded into the
current design, and shared template deletion for Badges, Labels and Business Cards.
Deletion retains server revisions, deletion metadata, and badge print records.

Installer: `SmartStudio-setup-0.1.48.exe`.
Size: 189667471 bytes.
SHA-256: `2c4356ea4f961f9883525ed71bc69c60ec2d87ba64da2614c6f5d954ec3ba333`.
Portable ZIP: `SmartStudio-0.1.48-windows-20261019-115213.zip`.
Size: 192056242 bytes.
SHA-256: `594f6a928bfbf279490a713870ba12394637e162263916c5d20f604569613058`.

Validation: all 165 Studio tests passed; security and whitespace checks passed.
The standard Windows packager passed startup checks. The exact installer was
extracted, Studio/client JAR identity checked, and native launcher/login rendering
verified. Extracted loose files and JARs contain no ONNX weights. Hosted model
catalogue verified. Publication and independent download evidence is under
`target/release-0.1.48-*`.

Public installer: https://downloads.deckers.gy/download/smartstudio/windows
Update the SmartStock server to 1.1.53 first. No running application was installed
or restarted. Live admin/history/deletion acceptance, database operations,
physical printing, installed upgrade/rollback, and macOS remain outstanding.
