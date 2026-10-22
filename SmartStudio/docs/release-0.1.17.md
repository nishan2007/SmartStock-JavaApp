# SmartStudio 0.1.17 Windows

Build 1017 adds searchable badge template selection, Delete for selected layers,
side and aspect-preserving corner resize handles, double-click canvas text
editing, and independent personal images in badge photo fields. Replace selected
image / photo supports both artwork and photo fields. Personal photos remain
excluded from template files. The client dependency remains SmartStock 1.1.31.

## Verification

All 51 SmartStudio tests passed during the Maven release package build. Tests
cover filtered template identity, resize geometry, inline text commit/cancel,
Delete during text editing, badge undo, independent photo rendering, and personal
photo exclusion from serialized templates. Repository security and whitespace
checks passed.

The packaged launcher and the launcher extracted from the exact installer passed
startup checks. The extracted application JAR matches the tested build:
`906ebe05f3d9eb53284a8d4ade66d3be5950458a553fad898aaf21568277d5ac`.
Badge and business-card windows rendered from extracted installer JARs at 820
and 1100 pixels; the badge rendering was visually inspected. Package JARs and
loose files contain no `.onnx` weights. ONNX Runtime and its third-party notices
remain present.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Installer | 219825501 | 3fef954578146fbc7bdebd397bbcce95f9be2a8a9b50dcb6163409b81131b974 |
| Portable ZIP | 228810712 | 29ed1c5760ef5ec8ad6ffd28f75e40be4fbc75dcc7d3935927fd3b9602c493f0 |

## Publication

The protected publisher uploaded and fully downloaded the installer, verified
its exact size and SHA-256, and published the SmartStudio release record.
Metadata readback matched version 0.1.17, build 1017, size, hash, published status,
and object path:
`smartstudio/windows/0.1.17/3fef954578146fbc7bdebd397bbcce95f9be2a8a9b50dcb6163409b81131b974/SmartStudio-setup-0.1.17.exe`.

The public installer portal selects 0.1.17; all other bindings were verified
unchanged. The first download during propagation returned 0.1.16. After the
new selection propagated, a complete public download returned HTTP 200 with
the 0.1.17 filename and exact size and SHA-256 above:
https://downloads.deckers.gy/download/smartstudio/windows

The portable ZIP remains local.

Live installed-app acceptance, Windows display scaling, authenticated store
processing, physical printer alignment and Magicard manual flips remain separate
checks. No live application installation or restart is part of publication.
macOS packaging and acceptance require a Mac.
