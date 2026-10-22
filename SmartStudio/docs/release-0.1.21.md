# SmartStudio 0.1.21 — template library and editable imports

Business Cards and Labels now open a searchable local library with a page
preview, original physical dimensions, page selection and editable-text counts.
The Template & insert menu reopens the library. Save to template library saves
reusable designs without merge records. The older transferred card folder is
also discovered; upgraded designs take precedence over their flattened copies.

The Windows editable importer runs the local LibreOffice Python/UNO runtime in
an isolated profile, with macros and link updates disabled. It imports separate
artwork layers, normal text, simple shapes and tables. Table cells retain their
row/column positions and merged spans and can be edited and moved separately.
Publisher WordArt wording, fonts, geometry and rotation are recovered from the
original OfficeArt records. LibreOffice leaves incomplete WordArt geometry;
recovered wording is rendered as scalable editable text above that artwork.
Curved/enveloped WordArt is not guaranteed to reproduce Publisher exactly.
Complex decorative shapes remain separately movable image layers.

Barcode / QR tools generate, edit and replace image barcodes. Code 128, Code 39,
EAN-13, EAN-8, UPC-A, ITF and QR are supported. Generated data is stored as text,
with no barcode image asset. Invalid data stops output. Recognizable individual
barcode images are decoded during import; images containing a small barcode
inside a whole design are preserved. Unreadable barcode pictures can be replaced
manually using their known data. QR modules stay square and bars render without
antialiasing seams. Physical scanning and printer alignment remain separate checks.

## Verification

- All 65 SmartStudio tests passed.
- Targeted publishing gate passed: 22 Publication tests, two artwork/archive
  regressions and one scan-back test exercising all seven barcode formats.
- The large-library regression saves and reopens 510 independent assets; the
  resource cap now matches the existing 10000-object bound. The expanded archive
  limit remains 48 MiB. Repeated imported artwork is deduplicated.
- Real source transfer: 72 business-card/ticket designs and 42 label designs,
  114 templates and 402 pages. All archives reopened, rendered, and passed a
  text edit/movement/archive round trip where editable text exists.
- The library audit counted 9369 objects, 3804 text fields, 108 individual table
  cells and 42 generated barcode/QR objects. Its TSV and local source-render
  evidence are outside the repository.
- A real merged-cell fixture retained five cells including a two-column merged
  heading. Actual Publisher tables included 44 and 64 separate cells.
- Native UI verification from the installed application classes covered library
  search, preview, opening a label, separately dragging and directly editing a
  recovered field, and creating a generated barcode layer.
- The packaged and installed launchers passed startup checks. The installed
  application and dependency files were checked against their package inputs.

64 original designs contain no recoverable text layer: their lettering remains
part of picture artwork. The picker identifies these explicitly. Reconstructing
that lettering, exact comparison in Microsoft Publisher, WordArt envelope
fidelity, physical barcode scanning and paper alignment are not certified by
these tests. Word/Photoshop sources were not converted; Office lock files were
excluded. Original source documents were not modified.

The previous SmartStudio session contained unsaved work and was preserved.
The updated build is installed alongside it. A versioned desktop shortcut opens
the new build; no existing matching shortcuts were found to retarget. SmartStock's original two processes stayed running. No database,
service, release publication or release metadata was changed.

Final portable ZIP: `SmartStudio-0.1.21-windows-20261016-132910.zip`, 234073362 bytes,
SHA-256 `790c90c8603b052ab310d73453f3e8b9422ec2a01e086b710c5301098b1ea834`.
Installed application JAR SHA-256:
`97cdc3b68148d693a51f8adcb4fda017a01415b774a7da0481e2aa91259d17b3`.
