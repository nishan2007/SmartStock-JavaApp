# Bill book fidelity audit in progress

Scope: all 324 active imported bill book archives, every page and element,
compared with original Publisher evidence. Conversion success is not acceptance.

Original saved Publisher CF_METAFILEPICT thumbnails provide independent first-page
evidence for 318 archives. The converter PDF is insufficient: it omits table
borders, WordArt and some source formatting. Native Publisher table widths,
heights and row/column dimensions are therefore read directly from Contents and
Escher streams. The thumbnail DIB is decoded with bounded dimensions and sizes.
Border recovery requires a dark line confirmed in the original preview; native
geometry correction does not imply that unverified borders are present.

The first native pass repaired 202 templates. A subsequent edge check repaired
197 templates. Investigation of the 92 initial out-of-bounds flags found changed
page order in multi-page conversions. Matching table geometry by original
position and width instead of directory page order repaired another 139
templates. These pass counts overlap and are not distinct-template totals.
All writes retain exact before-repair archives and TSV hash manifests outside
the repository. Original source files and subsequent user edits are retained.

Evidence: Documents/SmartStudio/Bill Book Transfer/fidelity-audit-20261021
and fidelity-audit-native-v2, plus library-cleanup-* manifests/previews.

Outstanding: complete visual comparison after geometry repairs; stale raster
crops that compress table headings; standalone black fragments by TOTAL in
A&S Industries; source logos/colour fidelity; other-page borders without a
native page thumbnail; archives without saved thumbnails; physical typography
and print proof. The first-page audit cannot approve all pages of 56 multi-page
templates. Full fidelity remains unproven. These new native repairs have not
been packaged/published, and local archive changes are not proof of shared
server revision updates.

Targeted regression tests cover native geometry recovery, reordered-page
matching, text editability, idempotence and not inventing borders on a white
reference. Tests do not prove visual identity.

Further pass: small solid-black exports below the previous 5 mm threshold were
recovered in 64 templates (366 exports). Reprojecting text crops to native table
geometry changed 195 templates (215 exports). A&S Industries now has readable
column headings and a clean TOTAL area. Its merged "4 Inch Blocks" label is
still missing visually, despite remaining in the editable text model. This
must be repaired from native text placement; the converted PDF crop cannot
establish the missing label's appearance. Do not approve this design or the
collection on the strength of these passes. These repair counts overlap.

Merged-cell lettering follow-up: the saved native preview establishes an ink
bounding rectangle inside each merged cell, excluding long border runs. Where
that evidence exists, the wording becomes a separate editable field at that
position, leaving the table cell geometry intact. Visible horizontal text bands
restore wrapped wording such as "6 Inch / Blocks" and "4 Inch / Blocks".
The combined cleanup pass changed 61 templates for 65 repair operations;
archive inspection identified six recovered merged-cell labels in three templates.
The A&S first-page
preview now displays both labels. This does not approve other pages or establish
pixel-perfect font fidelity; all recovered labels still require comparison.

Further multi-page verification fixed one-to-one native table matching: identical
table positions on two pages must not both resolve to the first page. This pass
changed 11 templates. Gray antialiased grid pixels in source thumbnails were
also below the old border detector threshold; the corrected detector recovered
23 table operations in 14 templates, including A. Khan.

Original transparent PNGs are recovered directly from Publisher EscherDelayStm
when their aspect ratio and sampled image content uniquely match an opaque
export. Alpha is preserved by restoring the exact original PNG bytes. Successive
passes repaired 14 image exports (six, five and three), with before-image backups.
AP Electrical's restored logo SHA-256 is
0741e02471350267ebe7f696087f0a66d7728e8b021bfd131b077dc3c87175fe;
archive extraction confirms exact equality with the original embedded PNG. Its
opaque black matte is gone. The AMOUNT column heading still shows clipping and
needs source-backed text correction. The overall collection is not accepted.

Current test reports: 222 Studio tests, no failures/errors or skips; repository
security and whitespace checks passed. Native image tests cover alpha recovery,
nonmatching-image preservation and rejecting truncated PNG records. These
checks do not establish complete visual fidelity or shared-server publication.

Native page-list audit: PublisherDrawingText now follows DOCUMENT_PAGE_LIST
instead of assigning page indices by directory order. Reserved dummy page
sequence IDs (0x10d, 0x110, 0x113 and 0x117) are excluded; their stale shape
records must not displace live text. This follows the upstream libmspub
parseDocumentChunk/getPageTypeBySeqNum interpretation. Zero-payload property
type 0 is accepted when traversing document metadata. Regression fixtures cover
reversed document order and dummy-page records.

A new native-word audit covers all 318 Publisher archives, comparing nonempty
native fields with editable text on the expected page. It flagged 21 archives
with 445 missing/misplaced wording entries; these are audit flags, not a count
of visually confirmed missing source elements. The Bono reimport restored its
business heading, address, contacts and tax wording, with a before-repair backup.
The 21 flagged archives are being reimported serially using the existing
LibreOffice worker and the corrected parser. Each replacement checks original
source and prior archive hashes, validates the new archive and saves every
page preview plus a manifest. Evidence lives in native-page-reimports-* and
missing-native-words.tsv outside Git. Reimport completion and full visual
acceptance are separate checks; no shared revision or application publication
is implied.

Clipped-heading follow-up: PDFTextStripperByArea compares native-row text with
an extended row region to identify a word split below its native cell. The
repair requires that specific evidence and uses the original preview's text
position. It recovered 12 operations across 10 templates; AP Electrical's
AMOUNT is fully visible without changing its other headings. Correct headings
must not be indiscriminately restyled from a small thumbnail.

The page-order reimport batch completed: 21 archives replaced with validated
archives and before-repair backups, zero conversion failures. A fresh native
word audit of all 318 Publisher templates reports zero missing/misplaced native
word flags. This does not establish visual fidelity; typography, graphics and
all-page comparison remain separate acceptance work.
