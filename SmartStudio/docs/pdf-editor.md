# PDF Editor

Sign in to **SmartStudio**, then choose **PDF Editor** from the workspace chooser.
SmartStudio opens, edits, and saves PDFs on this computer. When you use OCR,
SmartStudio sends the selected page images to the store server over the authenticated
HTTPS connection.

## Opening and organizing documents

Use **Open…** to replace the current document, **Add Files…** to merge more PDFs,
or drop files onto the page list or preview. The import dialog lets you order
the files and choose the insertion position. Passwords are requested for
encrypted files. Restricted documents require the owner's password. Digitally
signed documents require acknowledgment and become unsigned edited copies.
Exported edited copies are not password-protected; keep them in an appropriate
local folder if the source document contains private information.

Select page thumbnails with Ctrl/Cmd or Shift. **Select page range…** accepts
entries such as `1–3, 5, 8–10`; use `1-N` for all N pages. Drag selected thumbnails
to reorder them, or use **Move Before/After…**. Rotation, deletion, duplication,
extraction, splitting, blank/image page insertion, cropping, and PNG export are
available in the right panel. Cropping uses PDF points (72 points = one inch)
and changes the visible boundary; it does not erase hidden content.

Extract saves selected pages in their current document order. Split and PNG
export refuse existing `page-N` output files; choose an empty directory. If a
batch is cancelled, already completed output files remain available.

## Adding and editing content

Add text, images, highlights, rectangles, or a drawn signature. Drag an added
object on the preview to move it; select it in the object list and choose
**Edit selected object…** to change its coordinates, dimensions, text, font size,
or colour. Coordinates use the original PDF page space, measured from the
bottom-left. Added text uses Helvetica and its supported character set.

**Fill PDF form…** edits standard AcroForm fields; enter a checkbox/radio field's
PDF export value when required by the document. Dynamic XFA forms are not
supported. Imported fields receive unique names to avoid merge collisions.
Duplicated pages preserve field appearances as static artwork rather than
creating a second field sharing the original identity.

Page numbers can be placed at the top centre, bottom centre, bottom left, or
bottom right with a chosen starting value. Search works with existing text,
added text, and OCR text. A drawn signature is a visual mark, not a digital
signature. Existing PDF text is not reconstructed into editable paragraphs.

## Store-server OCR and scanned corrections

Update SmartStock on the store server and SmartStudio on the workstations.
On an x64 Windows store server, sign in to SmartStock as an administrator and
choose **Status → AI Models → Install PDF OCR**. The server downloads and tests
Tesseract with English language data. The runtime stays in the server profile
across app updates. Workstations can use it as soon as installation finishes,
without a server restart or a local Tesseract install.

For a manual server install, place Tesseract and its language data where the
SmartStock service account can run it. Without a managed runtime or a custom
path, SmartStock checks `%ProgramFiles%/Tesseract-OCR/tesseract.exe` on Windows
and then uses `tesseract` from the server process's PATH. The default language
is English (`eng`).

For a custom install or language, create
`~/.smartstock/profiles/<environment>/studio-ocr.properties` under the server
service account's home. Replace `<environment>` with `production` or `development`.
Each setting is optional. Use forward slashes for Windows paths. An explicit
executable or language-data directory, or a language other than English, selects
the manual server setup.

```properties
executable=C:/Program Files/Tesseract-OCR/tesseract.exe
dataDirectory=C:/Program Files/Tesseract-OCR/tessdata
language=eng+spa
```

In this example, both English and Spanish data must be installed. The server reads
the file for each request. If you changed the service account's PATH, restart the
server so the process inherits it. Sign in on a workstation with **Allow Studio**
enabled and choose **OCR → Check server OCR…** to test recognition on a built-in
sample. Workstations use the server's configured language automatically.

The server processes one page at a time and returns recognized words and their
positions. It removes its temporary page and result files after processing.
Pages are limited to 16 megapixels and 18 MiB as PNG. If OCR is unavailable, you
can continue editing and saving your PDF in SmartStudio.

Select pages and choose **OCR selected pages** to add searchable text. For
scanned corrections, OCR the page, then select **Correct scanned region…**.
Choose consecutive recognized words, enter replacement text, and adjust the
font, size, text colour, and background. Check the preview before applying.
OCR confidence and recognition vary with scan quality; review the output.

A correction replaces that page's content with the composed page image and
regenerates its searchable layer. Original text, annotations, and form widgets
on the corrected page are removed. Other pages retain their original PDF
content. Save and reopen a page with added objects before making scan
corrections. Re-run OCR after a correction to select another region.

Corrections and cropping are **not secure redaction**. Metadata, attachments,
bookmarks, and other document data are not comprehensively sanitized.

## Saving, undo, and printing

**Save As…** writes a new PDF, reopens it to verify the page count, and replaces
the destination only after verification. Existing files require overwrite
confirmation. Undo/redo is session-local and bounded by a 96 MiB PDF snapshot
budget (the most recent command is retained even if larger). Added objects stay
editable in the current session; reopening an exported PDF treats them as PDF
content. Closing or replacing a document prompts about unsaved edits.

The workspace previews pages before printing and uses the native print dialog.
Loading, rendering, OCR, export, and printing run in background workers. Cancel
stops SmartStudio from waiting for the current OCR request and prevents it from
sending further pages. The server may finish the current request before accepting
another. You can undo completed edits. Crop oversized pages before running OCR.

## Developer validation

```powershell
mvn -q -f SmartStock/pom.xml install
mvn -q -f SmartStudio/pom.xml test
& 'C:/Program Files/Git/bin/bash.exe' SmartStock/tools/security-check.sh
git diff --check
```

An optional isolated Swing check creates an invisible workspace, loads a sample
page, and renders desktop/minimum-size layouts under `SmartStudio/target/pdf-editor-preview`:

```powershell
mvn -q -f SmartStudio/pom.xml '-Dtest=gy.deckers.studio.pdfeditor.PdfEditorSwingTest' '-Dsmartstock.pdfEditorUiTest=true' '-Djava.awt.headless=false' test
```

To run the server OCR test with an installed Tesseract, pass the executable
location to Maven. For example, on Windows run:

```powershell
mvn -q -f SmartStock/pom.xml '-Dtest=StudioPdfOcrServiceTest' '-Dsmartstock.test.tesseract=C:/Program Files/Tesseract-OCR/tesseract.exe' test
```

For a deployed setup, use **Check server OCR…**, run OCR on a scanned PDF,
and apply a scanned-region correction from a paired workstation.
