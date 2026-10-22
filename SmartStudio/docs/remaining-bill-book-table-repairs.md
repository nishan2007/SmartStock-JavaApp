# Remaining bill-book table repairs — 22 October 2026

After the user accepted K&V and authorized the remaining designs, the shared
library was repaired through its authenticated HTTPS API. Historical archive
content hashes established that every existing server head matched a transferred
baseline. Each update used the current SHA as an optimistic conflict check,
preserved the template ID/ownership/sharing, retained a backup, and downloaded
the entire saved archive again to verify its hash and contents.

The first pass updated 220 existing designs. A follow-up improved 205 overlapping
designs: original Publisher thumbnails can be too small to confirm individual
row edges, so continuous native table rules are confirmed across adjoining cells.
Only source-confirmed rules are added, and merged cells are respected. The
experimental direct thumbnail tracing produced doubled lines and was discarded
before publication. The final templates use the native table coordinates.

The initially pending VISHAL CASH RECEIPT had a trailing-space title rejected by
the server's normalized name comparison. Its title was normalized, the upload
succeeded, and full read-back was verified. PublicationTemplates.clean now
normalizes those spaces, with a regression test. Eight previously skipped,
distinct source variants were also uploaded and verified.

Final evidence: 315 shared designs cover all 324 active local archives by their
editable content fingerprints; zero local contents are absent from the server.
The shared archives contain 23,308 editable line elements. Exact size/SHA and
content checks passed for downloaded copies. The final native-word audit covers
318 Publisher archives and reports zero flags. SmartStudio's 234 tests passed;
the core title regression, repository security check and whitespace check passed.

Evidence/backups/previews/manifests are outside Git under:
C:/Users/decke/Documents/SmartStudio/Remaining-bill-books-review-20261022.
Final catalogue status reported 163 OneDrive backups pending, with no backup
error flag; they continue in the existing server retry worker. Server saves and
LAN availability were verified independently of that off-site backup queue.

This repair does not certify every font metric, decorative element or later page
as an exact Publisher reproduction. Only the original first-page thumbnail is
available independently of LibreOffice; first-page evidence must not invent
borders on later pages. Cross-computer display and physical printing still need
real-device checks. No application release or live application install occurred.
