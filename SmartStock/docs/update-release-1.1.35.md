# SmartStock 1.1.35 Windows

Build 101035 adds the shared editable WordArt renderer and Publishing workspace
editor. It supports plain, arch up/down and wave text with font, bold/italic,
gradient, outline and shadow controls. Fields retain independent text, geometry
and rotation. The publication engine also carries the editable text/table-cell
and generated barcode/QR support used by SmartStudio 0.1.22. Every Publisher
effect is not automatically reconstructed.

Validation: the full Maven packaging gate ran 1,032 tests, 61 skipped, no
failures or errors. All 67 SmartStudio tests passed against the 1.1.35 dependency.
All 21 publisher/portal/package-validation Node tests passed. Repository security
and whitespace checks passed. Model-free ZIP and hosted catalogue verification
passed; no models changed or were republished.

The exact all-in-one installer was extracted in an isolated acceptance folder.
All 30 updater files match the installer payload. Its native launcher opened the
welcome window in a separate register profile and the acceptance process was
stopped. Live SmartStock processes were not restarted or installed over.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 166981250 | b1774728eaa8d9477e8e27a0267770053d6e5722ad2d5807b40fbd7f3198746b |
| All-in-one installer | 580057414 | 3ebbd03471f94cfa8276c2efe33bd2edc65443f9554329daa9fff6ccbc31549a |

Physical NFC badge login, live employee PIN setup, database/service upgrades,
update/rollback and printer/drawer checks remain separate acceptance steps.
macOS packaging and acceptance require a Mac and were not performed here.

Publication completed. The update ZIP and all-in-one installer were uploaded
and downloaded in full; exact size and SHA-256 matched before metadata or public
installer selection changed. Update metadata readback matched version 1.1.35,
build 101035, published status, content-addressed object path and the ZIP's
size/hash above. A complete unauthenticated public installer download returned
the correct filename and all 580057414 bytes with the installer SHA-256 above.
These published bytes must not be rebuilt under this version.

https://downloads.deckers.gy/download/windows
