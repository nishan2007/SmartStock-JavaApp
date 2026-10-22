# SmartStudio 0.1.10 Windows

Build 1010 adds front/back badge template pairing and manual card flipping for
Magicard 300 printers. Matching reverse fields/photos use the entered front data.
Printing uses single-sided jobs with a confirmation between front and back.
See [printing instructions](magicard-300-printing.md).

The release includes current badge/image workspace changes and SmartStock client
1.1.24. All 30 Maven tests pass. The final exact installer was extracted; both
the Studio JAR and SmartStock client JAR match the tested Maven artifacts. Its
native launcher and bundled Java runtime pass the startup check with an isolated
user home. The login preview was visually inspected. Model weights are excluded.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Installer | 154069919 | 94fedd18f8a5ed831244ba13b7202c2511a9b73cdc388cc1b066d70fff50a7a7 |
| Portable ZIP | 156436152 | 7acc420cb755a9437a62cf98387dd68ac37ab8628a189802bdbde4ef0d3b3ba9 |

The complete hosted installer download matched its exact size and SHA-256 before
release metadata was published. Metadata readback matched version, build,
platform, path, size, hash and published status. The protected installer portal
configuration was updated to select this verified Studio artifact.
Public HEAD returned HTTP 200, the 0.1.10 installer filename and 154069919 bytes.

Actual setup wizard/upgrade, physical Magicard feed/flip orientation and alignment,
large-photo processing against a live store and macOS remain separate checks.
No live Studio installation was changed.

Download: https://downloads.deckers.gy/download/smartstudio/windows
