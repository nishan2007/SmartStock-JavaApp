# SmartStudio 0.1.12 Windows

Build 1012 uses SmartStock client 1.1.27. Current template shapes, drag handling,
arrow-key movement and manual front/back printing are retained. Maven tests pass.
The exact installer was extracted; Studio/client JARs match the tested Maven
artifacts. Native startup passes with its bundled runtime and isolated user home.
No model weights are bundled.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Installer | 166646890 | 513b4b038538b76e5a9b71f3daf7fb8aeeb819bd16bb2f7c97208b819abaf79f |
| Portable ZIP | 169012134 | bd7a05b2754744ef4c003763ae78bb27cdbdeadfb65fd734b42ac3ceecc8e41b |

No live Studio installation was changed. Installed-app upgrade, macOS and actual
card printing remain separate acceptance checks.

Published after complete hosted download size/SHA-256 verification. Release
metadata readback matches the verified installer. The protected portal selection
was deployed for 0.1.12, and its public download returns the expected filename
and 166646890 bytes.

Download: https://downloads.deckers.gy/download/smartstudio/windows
