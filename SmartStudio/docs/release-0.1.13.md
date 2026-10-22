# SmartStudio 0.1.13 Windows

Build 1013 packages the current Studio features with the matching SmartStock
1.1.28 client. All 33 Maven tests pass. The exact extracted installer passes
native startup; packaged application and client JAR hashes match the tested
artifacts, and model weights are excluded.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Installer | 166647187 | 740182da182391687b4618b68783f7207dfd974380e45483ec181cbb0b611ec6 |
| Portable ZIP | 169012579 | 8f1b5b12ab3460b0e3079092cb0df5867c5994c234a87010a1c9b96576a31406 |

The complete hosted installer matched its size and SHA-256 before publication.
Published metadata readback matches. The complete public download also matches
the installer SHA-256 and size. Public download returns HTTP 200 with the
0.1.13 filename and 166647187 bytes. Installer portal deployed version
f53c66fe-1e16-4d7c-a7bc-b19c63461946 selects this verified Windows installer.

Live installation, authenticated store connection and physical card-printer
checks remain outstanding. macOS packaging requires a Mac.

Download: https://downloads.deckers.gy/download/smartstudio/windows
