# Windows update 1.0.221: catalogue studio photos

Published build 100221 using the existing R2/Supabase updater workflow. The publisher downloaded the exact uploaded ZIP, compared its size and SHA-256 with the local artifact, and then inserted the release record. The returned release row matched the artifact path and hash.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `smartstock-windows-1.0.221.zip` | 57,893,713 | `6b9ee5f10311022bd7275b8bcc5eda8a3e372643f9c3af48add7880cdb2dbaaa` |

R2 object: `windows/1.0.221/6b9ee5f10311022bd7275b8bcc5eda8a3e372643f9c3af48add7880cdb2dbaaa/smartstock-windows-1.0.221.zip`.

The package was built from the verified published 1.0.220 ZIP. A content comparison found only `MobileItemWebServer.class`, JAR version metadata, and the new `dependency/catalog-studio` tool and guide. All existing dependency files matched 1.0.220 byte for byte. The 1.0.221 JAR manifest reports `Implementation-Version: 1.0.221`.

The focused mobile item, image naming, and photo gallery tests passed. The repository security check, Python syntax check, and `git diff --check` passed. The full Maven suite had one unrelated failure: `StorefrontBundleTest` found an out-of-date compiled website asset for `website/src/ThreeDPrintingIntro.tsx` in the shared working tree. That asset was excluded from this update.

The installed Windows app, live service, database, and storefront display have not been checked after this update. No product photos were processed or imported. The workflow still needs its 20-product visual pilot and approval before a full catalogue run. There is no new installer artifact; the published file is an in-app updater ZIP.
