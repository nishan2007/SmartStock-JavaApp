# SmartStudio 0.1.38 Windows release

Adds Passport Pictures with four Guyana 35 × 45 mm photos or two US 2 × 2 inch
photos on portrait 4 × 6 photo paper. Includes photo loading, existing framing
controls, full-sheet preview, actual-size printing, and clipping/paper validation.
The print default uses exact printer media dimensions rather than Java's nearest
standard-media lookup, which incorrectly chooses the smaller Japanese postcard.
Uses the current SmartStock 1.1.41 client dependency.

Validation: 107 SmartStudio tests passed with no failures, errors, or skips;
repository security and whitespace checks passed. Extracted the final Inno
installer into an isolated acceptance directory and verified both main and client
dependency JAR hashes against build inputs. The extracted native launcher passed
its startup check. Both passport workspace previews were rendered using packaged
classes and visually reviewed. No ONNX weights are included; inference runtime
and licence resources remain present.

Final Windows installer: `SmartStudio-setup-0.1.38.exe`, 225226122 bytes.
SHA-256: `2805a706c5f8916a6a3c62c13221f13e871c665e038b183a9197a555ddd7396e`.
Build number: 1038.

Published to the SmartStudio Windows channel on 2026-10-17. The publisher fully
downloaded the hosted installer and verified its exact size and SHA-256 before
creating release metadata. Object path:
`smartstudio/windows/0.1.38/2805a706c5f8916a6a3c62c13221f13e871c665e038b183a9197a555ddd7396e/SmartStudio-setup-0.1.38.exe`.

Physical 4 × 6 printing, ruler measurement, photo tray/driver behavior, full
interactive installer execution, and installation/update acceptance on a live
workstation remain outstanding. Extraction and launcher checks do not prove
those behaviors. No live store installation, restart, or database change was
performed. No macOS package was built.
