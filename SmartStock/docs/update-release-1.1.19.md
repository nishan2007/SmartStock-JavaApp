# SmartStock 1.1.19 and SmartStudio 0.1.7

Windows builds 101019 and 1007. Packages include the current workspace application
changes, combined background removal/CPU enhancement and corrected SmartStudio
native image-drop handoff. SmartStudio retains the Badge Cards/template workspace.
The SmartStudio dependency uses the same SmartStock 1.1.19 client library.

## Validation

- SmartStock Maven suite: 913 tests, zero failures/errors, 50 skipped in the recorded
  packaging reports. SmartStudio: 27 tests, zero failures/errors/skips.
- Additional real pinned-model checks passed for offline enhancement at both scales,
  removal followed by enhancement, transparency and source preservation.
- Repository security, whitespace and browser JavaScript syntax checks passed.
- Updater archive passed model-free/layout validation and hosted model catalogue
  verification. Installer and updater contain identical SmartStock JAR bytes.
  The packaged JAR's entry contents match the tested Maven JAR; ZIP entry timestamps
  differ between the Maven install and packaging invocations.
- Exact installer payloads were extracted and their native Windows launchers passed
  startup checks with isolated user homes. SmartStudio's installer JAR matches its
  Maven JAR. No model weights were found in either application payload.
- The exact updater JAR ran offline removal and 2x/4x enhancement with both bundled
  Windows runtimes. Output dimensions, transparency and visible foreground passed.
- Full setup wizard/service installation, live staff permissions, WhatsApp drops,
  representative large customer photos, upgrade/rollback, live database operations,
  physical printers/drawers/NFC and macOS packaging remain separate checks.
  No running store installation or Windows service was changed.

## Exact artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| SmartStock updater ZIP | 133349186 | `e210f307786af924ce30d26dd3e52b6305418b74d04eba26ed15df3fd1858635` |
| SmartStock all-in-one installer | 546698621 | `6766d0fe24ecbca3052dd60b5e614dc12d7003a4c3241fc11489d475af41a1c2` |
| Model migration bridge | 3318 | `2937ad910ee2180f561e2732c9d0206ecdf27691ba83464e6cf7b34fd146eddd` |
| SmartStudio installer | 138182450 | `a2474f23358f02e7d581c92bcb62a5b9ed274ca5e75fed548a13d0d57f8ed96d` |

## Publication

SmartStock updater 1.1.19 and SmartStudio installer 0.1.7 are published. Full hosted
downloads matched their local size and SHA-256 before creating release metadata.
Both published rows were read back and matched Windows platform, version, build,
object path, exact byte size, SHA-256 and published status. The SmartStock full installer was uploaded, downloaded completely and verified
against the recorded size/SHA-256 before updating the permanent selection.
Public Windows installer HEAD returned HTTP 200, filename
`smartstock-windows-setup-1.1.19.exe` and length 546698621 bytes.
Download: <https://downloads.deckers.gy/download/windows>.

Update the SmartStock server first, then registers and SmartStudio. Enhancement
weights install separately through Status > AI Models from the pinned upstream
source; the existing Deckers Fast/Best catalogue was not changed. Sign in again
or reload the browser after installing enhancement. Applications and rollback
folders remain model-free. See [image enhancement](image-enhancement.md) and
[SmartStudio drop handoff](../../SmartStudio/docs/image-drop-fix.md).
