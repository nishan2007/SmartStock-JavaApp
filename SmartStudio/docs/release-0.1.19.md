# SmartStudio 0.1.19 Windows

Build 1019 packages the shared badge-library client, cached offline editing,
revision conflict protection, folder import, and Labels workspace. Labels retain
per-template dimensions, import Publisher page size, show current dimensions and
offer resizing with optional artwork scaling. The minimum-width toolbar and
initial Labels status were corrected during packaged visual review.

Shared templates require an installed SmartStock 1.1.34-or-newer store server.
The existing template folder must be imported once. This release does not migrate
personal photos or card details. Offline badge edits remain temporary and cannot
be saved until reconnecting; no offline upload queue is created.

## Validation

The complete 60-test SmartStudio Maven packaging gate passed. After adding the
minimum-width Labels regression, the targeted workspace/Labels gate passed,
bringing the retained test reports to 61 tests with zero failures/errors.
Repository security and whitespace checks passed.

The final packaged launcher and launcher extracted from the exact installer
passed startup checks. Extracted application and SmartStock 1.1.34 dependency
JARs match their verified build inputs. Badge, business-card and Labels windows
rendered from the extracted JARs at 820 and 1100 pixels. The corrected minimum-
width Labels rendering was visually inspected.

JARs and loose files were verified to exclude `.onnx` model weights. Inference
runtime libraries remain packaged. No running store application was installed
or restarted.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Installer | 225053091 | ebb39c294d8491b6c029a0a7f41cae9bcc61da812bc4805c927c205185c197e2 |
| Portable ZIP | 234031473 | cd73d8cc763c69b7a757bcb29f3b7428ad044acc100121bf2ea41ff85e06730d |
| Application JAR | 403881 | 5c4f9acbe7e9e9d3a7654f8d21ceef49d0a4229374b7119d7524866d5941e1ae |

An earlier candidate was stopped during upload, before release metadata was
published, when visual review found the Labels toolbar/status issues. Only the
final installer bytes above are eligible for this release.

## Outstanding live checks

Install the server update before the SmartStudio update, import the template
folder once, and verify two-computer sharing, offline/reconnect behavior, save
conflicts and real OneDrive revision backup. Actual LAN throughput, installed
Windows scaling, real native Publisher conversion and physical label/card print
alignment remain separate checks. macOS packaging requires a Mac.

## Publication

The protected publisher uploaded the final installer and fully downloaded it,
verifying all 225053091 bytes and SHA-256 before publishing the release record.
Metadata readback matched version 0.1.19, build 1019, published status, exact size,
hash and object path:
`smartstudio/windows/0.1.19/ebb39c294d8491b6c029a0a7f41cae9bcc61da812bc4805c927c205185c197e2/SmartStudio-setup-0.1.19.exe`.

The portal now selects the final installer; all other bindings were verified
unchanged. A full public download returned HTTP 200, the correct 0.1.19 filename,
and the exact size/SHA-256 above. The final portable ZIP remains local.

https://downloads.deckers.gy/download/smartstudio/windows
