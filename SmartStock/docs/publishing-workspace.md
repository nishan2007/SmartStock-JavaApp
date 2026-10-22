# SmartStock Publishing Workspace

Open **Operations → Publishing Workspace**. Price Tag Printing has a **Publishing
workspace…** button that copies the cart into merge rows. Custom-order Files and
Design Approval has a **Publishing Workspace** button that copies an existing
design and its available images into a separate publication. Neither action
changes an order design or approved customer proof.

## Design and save

Start from a Label, Card, Flyer, or Booklet preset under Project. Use the toolbar
to add text, shapes, lines, tables, images, QR codes, or Code 128 barcodes. Select
objects on the canvas or Layers tab; Shift selects several. Drag selected objects
to move them, or enter exact geometry in the inspector and click Apply properties.
After clicking an object on the canvas, arrow keys move it by 0.1 mm; hold Shift
for 1 mm steps. Locked objects remain fixed. Text-entry fields keep their normal
arrow-key editing behavior.
The Arrange menu provides grouping, locking, stacking, alignment, and distribution.
Ctrl+C/Ctrl+V copies objects between pages within the workspace; Ctrl+X cuts them.
Dimensions are stored in millimetres; MM, CM, and IN change the displayed units.

Text frames support font, size, bold, italic, and columns. Tables use tabs between
cells and newlines between rows. Image crops use normalized x,y,width,height values
between zero and one. A `none` fill or stroke makes that part of a shape transparent.
Styles save and reapply object appearance. Undo/redo tracks local document edits.

Add, rename, and delete pages in the navigator. Masters switches the navigator to
master pages; Apply master assigns a master to a normal page. Insert `{{page}}`
for page numbering. To continue text across frames or pages, select a text frame,
choose Publication → Link selected text frames, and use the same story name in
each frame. Flow follows page order, then object order. Editing story text in any
linked frame edits that story. Preflight reports text remaining after its last frame.

Publication → Page dimensions explicitly offers Preserve object sizes or Scale
contents. Guides and rulers help position objects; Snap uses a 1 mm grid.

Save shared writes through the authenticated store LAN API. The server stores
projects and their asset archive in local PostgreSQL, scoped to the current store.
Revision conflicts require reopening the shared version or saving as a new project.
Mark as shared template saves a reusable template; opening a shared template
creates a new project rather than editing it accidentally. Edit shared template
opens a template for deliberate revision. Template management
requires COMPANY_PREFERENCES or MANAGE_CUSTOM_ORDER_ITEMS on the server.
Workspace access requires VIEW_INVENTORY, CREATE_CUSTOM_ORDER, or MANAGE_CUSTOM_ORDERS.
Customer and order merge sources require their respective business permissions.

Portable `.sspub` archives contain versioned document JSON and image assets. They
can be imported/exported without a store connection. Recovery copies are written
every 30 seconds while dirty to the current user's `.smartstock/publications`
folder, with store/user-specific filenames. Project → Recover autosave opens one
as a new project. Recovery copies persist after closing; they may contain business
data and should be removed through normal file management when no longer needed.

## Fill and print sheets

Sheet setup controls paper size, independent margins, horizontal/vertical gaps,
bleed, calibration offsets, quantities, and skipped starting positions. A4 and
Letter presets include both orientations; custom paper sizes are supported.

Maximize copies compares uniform and mixed-orientation shelf layouts. Allow
rotated copies is optional. Item dimensions and minimum gaps never change. The
result is the best deterministic candidate found, not a proof of optimal packing.
The preview reports copies per sheet, total sheets, and occupied usable area.
Quantity zero fills one sheet. Skip applies to the first sheet and must be less
than its capacity. With merge data, each row's `quantity` controls its copy count
(default one), and sheet quantity is ignored.

Design / sheet toggles between editing and the imposed output. Choose the sheet
number to inspect later sheets. Export and print use an inclusive sheet range.
Printing uses actual size and rejects unsupported paper sizes or margins outside
the selected printer's printable area. Set zero margins only for a printer that
supports borderless output. Calibration and back-side correction values are mm.

Duplex pairs consecutive publication pages as fronts and backs. An unmatched last
front has a blank back. Back mapping can mirror positions for long-edge or
short-edge feeding, or keep the same positions. Booklet output pads to multiples
of four and orders pages for folding; its paper must fit two publication pages
side by side at their exact sizes. Booklets also support merge rows/copy quantities.
Select the matching duplex mapping and verify the driver's flip behavior with a
physical test sheet before production printing.

PDF export uses the same physical-coordinate painter as preview and direct
printing, rasterized at 300 dpi. Text in exported PDFs is artwork rather than
selectable vector text. PNG exports one selected sheet at 300 dpi pixel dimensions.
Both are intended for shop printing, not CMYK/spot-color/PDF-X prepress.
Preflight reports missing images/fonts, clipping, low image resolution, text
overflow, and missing merge fields. Missing merge fields block output. Crop marks
need additional margins/gaps to avoid clipping or marks touching adjacent artwork.

## Merge and import

Merge accepts CSV with quoted fields and embedded newlines, plus permitted
SmartStock products, customers, and orders. Preview displays the first 100 rows;
field mapping copies a source column to a named template field. Use `{{name}}`,
`{{price}}`, `{{code}}`, or another mapped field in text, table, barcode, or QR
objects. Map a positive whole-number column to `quantity` for variable counts.
Source records and templates are not modified by printing a merge.

PDF pages import as raster artwork. Publisher import requires a locally installed
LibreOffice; select its `soffice.exe` (Windows) or `soffice` executable (macOS) when
prompted. Conversion runs headlessly with an isolated temporary profile and a
timeout. The original `.pub` file is preserved.

Supported ODG text, rectangle, ellipse, and image objects become editable.
Complex objects/transforms are reported, and affected pages gain a separate visual
reference page that is excluded from printing. Compare the editable reconstruction
with that reference and repair missing content before output. Include / exclude
page from printing can switch to the reference artwork as the printable version.
Text styles are
approximate; mixed formatting, advanced tables, gradients, and linked-text
reconstruction from Publisher are not guaranteed. `.pub` export is not provided.

## Deployment and verification

The local migration `20261012120000_publication_workspace.sql` is registered in
the canonical local schema contract. Fresh provisioning on both installer paths
uses that contract; existing stores get its transactional upgrade. The immutable
v1 baseline is preserved. Project rows join the location-scoped recovery manifest;
in-progress upload buffers are ephemeral and excluded from recovery.

Automated tests cover packing, units, archives, merge, rendering, PDF dimensions,
recovery, and shared persistence/conflicts against a disposable PostgreSQL cluster.
The PostgreSQL test uses `SMARTSTOCK_TEST_POSTGRES_BIN` or the standard Windows
PostgreSQL 17 bin path. Without that runtime it is skipped. It never uses store
database configuration. An optional non-headless Swing test renders a hidden
window into `target/publishing-workspace-preview.png`.

Outstanding deployment checks: real Publisher files through LibreOffice, macOS
UI/conversion, authenticated multi-computer saving, physical printer dimensions,
borderless margins, duplex/booklet alignment, and barcode scanning. No release
publication or live-store installation is performed by these changes.
