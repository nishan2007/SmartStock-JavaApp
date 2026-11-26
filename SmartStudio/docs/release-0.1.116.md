# SmartStudio 0.1.116 Windows quick update

## PDF Editor

- **OCR selected pages** and **Correct scanned region** use the authenticated SmartStock store server. **Check server OCR** tests availability and shows the configured language.
- The PDF stays on the workstation. The server receives the rendered page for recognition, and SmartStudio applies searchable text or the confirmed correction.
- Insert barcodes and QR codes directly in a PDF. They remain scannable after saving and reopening.

## Design tools

- Badge Cards supports editable barcodes, QR codes, tables and WordArt. Double-click to edit. Older flattened codes can be reinserted as editable objects.
- Business Cards and Labels support photo placeholders. Replacing a photo retains the frame, rotation, zoom and relative crop. Empty placeholders stay blank in output.
- **Send to design** places a processed Image Studio result directly into an open design, including the PDF Editor.
- Relevant ribbons expose insert, alignment and layer controls. This build includes shared formatting, photo and template-workspace improvements and keeps the visible Front/Back badge controls.

## Orealla / Siparuta records

- **Data → DB** shows saved fields in sortable columns. Search spans all saved fields, and selecting a record previews the permit and saved portrait.
- **Load record** fills the badge for review before printing and preserves the saved permit number.

## Rollout

This update uses the SmartStock 1.1.152 client. Update the store server first. A SmartStock administrator can install the managed OCR runtime from **Status → AI Models → Install PDF OCR**.

## Release verification

- Windows build 1116. The update feed artifact is 175467491 bytes.
- SHA-256: fa8e4e0c0bb72941bc4882f87301571af54dd4bdb4eec66305b3fc29692a4d65.
- The full hosted artifact was downloaded and verified before publication. Independent readback matched the artifact and release notes.
- Maven reported 408 tests with zero failures or errors and one skip. The isolated 0.1.115 upgrade verified the application and SmartStock client JARs, preserved its runtime, and passed native startup. The updater contains no model weights.
