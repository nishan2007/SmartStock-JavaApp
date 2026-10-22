# SmartStudio 0.1.18 Windows

Build 1018 adds image insertion/paste as new layers, per-photo nondestructive
framing, and optional background/outline colors. New photos start fitted to the
existing photo-box dimensions. Personal images and framing remain excluded from
saved templates; field appearance is saved. The client inactivity timer is
removed. Fully removing session timeouts requires the matching store server;
the SmartStock 1.1.32 dependency package contains that server policy change.

## Validation

All 57 SmartStudio tests passed during Maven packaging, including insertion,
photo framing, transparency, outline rendering, export consistency and template
privacy tests. Repository security and whitespace checks passed.

Packaged and exact extracted installer launchers passed startup checks. The
extracted application JAR matches the tested build, and the extracted client
dependency exactly matches SmartStock's 1.1.32 package. Badge/business-card
workspace renders at 820 and 1100 pixels succeeded. The badge workspace and
complete photo-framing dialog were visually inspected from packaged JARs.

Package JARs and loose files were verified model-free. ONNX Runtime and its
third-party notices remain present.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Installer | 222968268 | 82bf39b49c1f119e70c6b1b872971602b591cbd826f4dbdbf248ae05d1395dd9 |
| Portable ZIP | 231955911 | 3e87bedd1ccad10b3b04cacebc0d18bdf4e72b25f535d7e8fed0a23be481fb5c |
| Application JAR | 390115 | 5c32ccae83b19355b9abc95c1b7b07bce894760501fddc79332d4c8e9aecfab2 |

No live installation was changed or restarted. Installed-app acceptance,
authenticated store processing, Windows scaling and physical printing remain
separate checks. macOS packaging requires a Mac.

## Publication

The protected publisher uploaded the installer and fully downloaded it to verify
size and SHA-256 before publishing release metadata. Metadata readback verified
0.1.18, build 1018, published status, exact size, hash and object path:
`smartstudio/windows/0.1.18/82bf39b49c1f119e70c6b1b872971602b591cbd826f4dbdbf248ae05d1395dd9/SmartStudio-setup-0.1.18.exe`.

The public portal now selects this installer. All other bindings were verified
unchanged. A complete public download returned HTTP 200 with the correct 0.1.18
filename and exact size/SHA-256 above. The portable ZIP remains local.

https://downloads.deckers.gy/download/smartstudio/windows
