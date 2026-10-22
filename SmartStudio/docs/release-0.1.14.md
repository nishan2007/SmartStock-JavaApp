# SmartStudio 0.1.14 Windows

Build 1014 adds business-card templates, multi-page editing, table and Excel
mail merge, independent page numbering, mixed/separate page imposition, complete
sheet previews, Letter/Legal/A4/11 × 17/custom stock and PDF export. The client
dependency is SmartStock 1.1.30.

All 42 SmartStudio Maven tests and the SmartStock Maven suite passed. The
repository security script and whitespace checks passed. The packaged launcher
and the exact extracted installer launcher passed startup checks. Application
and client JAR SHA-256 values match the tested build. Package JARs contain no
ONNX model weights; the inference runtime is retained.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Installer | 184093924 | 6110565816cf70d865ae0f7b410e781522d132a0489b8833d672c7dabd446b74 |
| Portable ZIP | 186495208 | c5d34f392dad73d798dcd0ffe579f10975d3accd138e8fb4f0d21d0f1329e5b8 |

The installer was uploaded and fully downloaded to verify size and SHA-256
before publishing update metadata. Metadata readback matches version, build,
artifact path, size and hash. The portal's SmartStudio Windows selection was
updated through its settings API; all other bindings were preserved.
The complete public download returned HTTP 200, the 0.1.14 filename and exact
installer size/SHA-256. The portable ZIP is retained locally.

Download: [SmartStudio for Windows](https://downloads.deckers.gy/download/smartstudio/windows)

Live installation, authenticated store connection, printer driver custom-paper
acceptance, physical card size/cut alignment and hardware checks remain
outstanding. No live store application was installed or restarted. macOS
packaging and acceptance require a Mac.
