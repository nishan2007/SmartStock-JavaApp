# SmartStudio 0.1.16 Windows

Build 1016 introduces the canvas-first workspace, startup workspace/template
selection, badge template previews, collapsible editing panels, direct object
properties, compact template/output/account menus, and Original/Result/Compare
image views. The client dependency remains SmartStock 1.1.31.

## Verification

- All 45 SmartStudio Maven tests passed during `mvn -q -f SmartStudio/pom.xml package`.
- The 27 shared card architecture and Publisher rendering/packing checks passed
  during workspace validation.
- Repository security and SmartStudio whitespace checks passed.
- The packaged launcher and the launcher extracted from the exact installer
  passed startup checks. The extracted application JAR SHA-256 matches the
  tested build (`d53e66b8b08132bed7c5e72d6d840983086f9fbecaf0ec19f2922963cbba2c3d`).
- Badge and business-card windows rendered successfully from the extracted
  installer JARs at 820 and 1100 pixels and were visually inspected.
- All extracted JARs and loose files were scanned for `.onnx` weights; none
  were present. ONNX Runtime and its third-party notices remain packaged.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Installer | 219807721 | 37cea4b2bbd96218cab345c0ecbcc87ff397b5cfd4fb5e5b5df48164eabc3a59 |
| Portable ZIP | 228794111 | cd2cc688fc6a2cbd8ffde4d7ca39697f9e0eab7c8e3729a9bf63d72969ea7068 |

## Publication

The existing protected publisher uploaded the installer to
`smartstudio/windows/0.1.16/37cea4b2bbd96218cab345c0ecbcc87ff397b5cfd4fb5e5b5df48164eabc3a59/SmartStudio-setup-0.1.16.exe`.
It downloaded and verified the complete stored object before publishing the
SmartStudio app-release record. Metadata readback verified version, build,
size, SHA-256, object path and published status.

The installer portal now selects this exact artifact. Its deployed Worker code
was retained and all other bindings were verified unchanged. The public download
returned HTTP 200, filename `SmartStudio-setup-0.1.16.exe`, and the exact size and
SHA-256 listed above:

https://downloads.deckers.gy/download/smartstudio/windows

The portable ZIP remains local. No application was installed into the live store
or restarted. Full installed-app acceptance, Windows display scaling,
authenticated store processing, physical printer alignment and Magicard manual
flips remain outstanding. macOS packaging and acceptance require a Mac.
