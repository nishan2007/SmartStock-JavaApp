# Bill books

Bill Books has a separate template picker and local import library at
`<SmartStudio profile>/template-library/bill-books`. It uses the existing
publication editor: separate text/artwork layers, page editing, number series,
merge records, linked Riso sub-pages, PDF output and sheet printing.
New designs start at half Letter (5.5 × 8.5 inches). Imported designs retain
their physical dimensions. The bill book workspace restores the saved sheet
size; larger originals must not silently revert to Letter.

Starting with SmartStock server 1.1.68 and SmartStudio 0.1.61, Bill Books uses
the authenticated store server in a separate BILL_BOOKS category. Existing
store permissions, ownership/sharing, immutable revisions, verified local
caches and OneDrive backups apply. No database schema changes are required.
Update the server first and then the Studio computers. Sign in online on the
computer containing the converted bill books to upload them. Other computers
signed in to the same authorized store then download the shared templates.
Publishing/installing the app alone does not upload customer template files.
Newly converted local files are discovered during subsequent synchronization;
completed uploads are checkpointed, and remote edits are retained. Original
Publisher files and supporting Word/Excel documents stay in the local source
backup; only cleaned editable .sspub designs enter the shared template library.

## Numbering and mail merge

In Data, use **Add number field** to insert a movable text field on the current
page. Position it on the bill and adjust its font, size and colour with the
existing text controls. **Fill number series** generates the selected column
using a starting number, step, count and minimum digits (for example 00001).
Use the same `{{number}}` field on duplicate/triplicate pages when they must
share each record's number; page-specific fields can have independent series.
**Connect Excel** can instead load columns whose headers match `{{field}}`
placeholders. The quantity column controls repeat copies of each record.

Under Print setup, **Print content** selects **Full template + numbers** or
**Numbers only (preprinted books)** for Preview, Print, PDF and Riso output.
For numbers only, enter the merge column names in **Number columns**, separated
by commas. Non-number text, images, table backgrounds, master artwork and crop
marks are omitted from that job; the numbered text keeps its position and style.
Adding a number field includes its column in this list automatically. The saved
design always retains all artwork regardless of the selected output mode.
Missing number fields or incomplete number records stop numbers-only output.

Print a physical proof at actual size before a production run. The same sheet
setup, placement and record order are used in both modes. Printer hardware
margins, paper feed and alignment on preprinted books require a physical check.

## Print content

In Bill Books, open Print setup and choose Print content:

- Full template + numbers prints the artwork and filled numbering fields.
- Numbers only (preprinted books) prints the selected numbering fields.
- Template only (no numbering) prints the artwork with those numbering fields
  blank, preserving other text and merge fields. Set Copies (no merge) when
  the data table contains only numbering. Preview and Export PDF use this mode too.

Number columns identifies the numbering fields for the last two modes. These
choices affect the output only; saved templates retain their numbering fields.
Numbers embedded in an image or typed as ordinary text remain artwork.

## Numbering position order

Print setup provides Numbering order: row or column traversal from any corner,
the existing layout order, or Custom position order. Position map shows IDs
from top to bottom and left to right on the front of the sheet. Custom positions
must list every ID exactly once; changing paper or rotation can change the IDs.

For two columns and three rows:

- Rows: right to left, top first gives top right, top left, middle right,
  middle left, bottom right, bottom left (custom IDs `2,1,4,3,6,5`).
- Columns: top to bottom, right first gives right top, middle, bottom, then
  left top, middle, bottom (custom IDs `2,4,6,1,3,5`).

Without Cut and stack, records continue through the selected positions on each
sheet. Enable Cut and stack for consecutive piles after cutting: 1,000 values
in two positions produce 500 sheets, with 1–500 in the first position and
501–1000 in the second. Choose a right-first order to make the right pile first.
The number of sheets is calculated from the total printed record quantities
and the actual layout capacity. An incomplete last pile leaves blank positions.
Keep the printed sheets in their intended stack order when cutting; printer
face-up/face-down delivery needs a physical proof.

Cut and stack requires Each page on separate sheets. Two-sided output keeps
each back paired with its front. Numbering direction and stacking settings are
saved with the template and shared by Preview, Print and Export PDF. Review the
preview after changes to paper, margins, gaps or rotation. Ordinary typed or
image-based numbers are not merge numbering fields.

## Transfer details

Use `tools/transfer-bill-books.ps1` with explicit `SourceRoot`,
`DestinationRoot` and `EvidenceRoot` paths. The source root contains
`Bill Books` and `ORIGINAL BILLBOOK` folders. LibreOffice's Python/UNO runtime
is required. Build with the shared SmartStock dependency installed locally.

The transfer retains relative folders and source extensions, so similar names
do not overwrite each other. It reads originals without modifying them, writes
editable `.sspub` archives, reopens each archive to check pages and assets,
and records source/archive SHA-256, text counts and missing fonts in
`transfer.tsv`. Source receipts allow a subsequent run to retain already
transferred designs. Existing designs are never replaced by the transfer.
The archive audit only repairs archives matching a recorded transfer hash;
subsequent user edits are skipped. Repair backups are kept with the evidence.

Reference PDFs and editable previews are retained outside the library.
Conversion success is not a visual approval. Check every page against the
original in Publisher, including fonts, tables, logos, numbering and linework.
LibreOffice's reference PDF is also a conversion and may differ from Publisher.
Picture-only lettering remains artwork and cannot be promised as editable text.
The audit flags templates with no editable text and missing fonts. Publisher
merge fields that export as entirely black bitmaps use recovered editable text
instead; their font and field geometry still require review.

The initial collection includes a PowerPoint bill book with literal zero-height
table rows. It fails validation and needs source-table repair; do not approve
it by inventing replacement row heights. Supporting Word/Excel/image files are
preserved as originals, not converted by the drawing importer.

Keep customer originals, manifests and review evidence outside Git. A physical
proof is required to verify sheet dimensions, margins, numbering, duplicate or
triplicate order and Riso/printer alignment. Automated tests do not establish
installed-app or physical print behavior.
