# SmartStudio 0.1.103

Windows quick updater, build 1103. Removes the 100 MiB per-artwork file cap for banner PNG/JPEG import, paste, save/reopen, printing and PDF export. Resampled PNGs stream to temporary files without reducing resolution to meet an encoded-file limit; temporary job files are removed after print/export. Original artwork and physical dimensions stay unchanged. Existing 100 MP/heap limits and the 5 GiB total template budget remain; byte-array import remains limited by Java memory/array capacity.

Includes current Studio changes and the native banner resolution correction from 0.1.102. Uses the rebuilt SmartStock 1.1.118 client library; no SmartStock app release was published.

Validation: SmartStudio Maven package/full suite, 379 tests, zero failures/errors, one skipped; shared Publication tests passed, including oversized template rejection, archive-path validation and asset staging above 100 MiB. Valid 101 MiB PNG import/save/reopen/clipboard regression passed. Resampled file-backed PDF output and cleanup passed. Repository security check and git diff --check passed.

Exact updater installed over an isolated copy of the current app on D:. Application/client JAR hashes matched; runtime preserved; model weights excluded; runtime/notices retained; native installed startup passed. Existing shortcuts and shell registrations restored. Running installation was not changed. Canon GP-4600S driver preview and physical printing, database, service, cutter, drawer and NFC checks remain separate and unperformed.

C: had insufficient packaging space. Automatic approval review blocked deletion of temporary build copies, so no deletion occurred. Packaging and isolated acceptance used D: instead. Initial test attempts failed due to a stale limit assertion, a disk-heavy fixture and overlapping dependency replacement; these were corrected and the final gates passed.

Artifact: D:/SmartStudio-release-0.1.103/SmartStudio-update-0.1.103.exe
Size: 173741711 bytes
SHA-256: a4cb938ee4a66150a11980b1f0a94d47f59fbbc9ce3ff7d4efb230e71313ccd8

Published after full hosted download/size/SHA-256 verification. Independent metadata read-back matched version 0.1.103, build 1103, object path, bucket, size, hash and published status. Existing first-install selection remains unchanged.
