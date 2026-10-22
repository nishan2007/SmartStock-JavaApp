# SmartStudio 0.1.15 Windows

Build 1015 includes Business Cards, multi-page templates, mail merge, numbering,
sheet previews and PDF export, plus badge artwork caching, template zoom controls,
Ctrl + mouse wheel zoom, and Undo/Redo with Ctrl+Z, Ctrl+Y and Ctrl+Shift+Z.
The client dependency is SmartStock 1.1.31.

All 42 SmartStudio Maven tests passed. Packaged and exact extracted installer
launchers passed startup checks. The application JAR matches the tested build.
Every client JAR entry matches SmartStock's final package; archive timestamps
account for different whole-JAR hashes between Maven builds.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Installer | 219797550 | ae21ab1aceab8a27f234dce161851bbf53394065a298e158408735fb34b8d5fa |
| Portable ZIP | 228783459 | d7ce5a6215f6b9c5b7b524bd048c34a61748d6c537623ea3dc15a356e42570d2 |

The installer upload was fully downloaded and verified before release metadata
publication. Metadata readback matched the exact artifact, version and build.
The portal selects this verified installer and retains its other bindings.
The portable ZIP is retained locally.
The complete public installer download returned HTTP 200 with the correct
filename, exact size and SHA-256.

Live installation, authenticated store connection, interactive template editing,
printer driver custom-paper acceptance and physical print alignment remain
outstanding. No running store application was installed or restarted. macOS
packaging and acceptance require a Mac.
