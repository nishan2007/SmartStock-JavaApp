# Quotations and invoices — Document Studio design QA

Date: 2026-11-23

final result: failed — previous implementation did not match the approved layout

## Scope and references

Implements the selected third design in the existing Java/Swing application: a searchable document list or editor beside a portrait document preview. The user's requested adaptation takes precedence over the mock document: existing Grid and Compact quotation/invoice templates and the existing delivery-note template remain the print formats. The app's navigation, permissions, payment/account-charge flow, and delivery posting rules remain connected to the existing services.

The selected image and refined letter-format reference were inspected together with production-component renders. Reference images remain in the conversation's generated-image folder. This is an adaptation to the existing desktop application, not a web prototype or a replacement set of invoice templates.

## Rendered evidence

Evidence directory: `SmartStock/target/document-studio/` (paths below are relative to it).

- `quotation-editor-wide.png`: actual quotation editor at 1440 × 850 logical pixels, including customer selection, line items, server total and preview.
- `quotation-editor-1280.png`: 1280 × 680 logical pixels; toolbar wraps, controls remain accessible, portrait sheet retains its aspect ratio.
- `quotation-editor-small.png`: 1024 × 680; editor and preview become separate tabs instead of squeezing their contents.
- `custom-item-wide.png`, `custom-item-small.png`, `stock-item-wide.png`: real item-dialog components. Proposed item changes are rendered against a copy of the quotation; cancel does not modify the draft.
- `document-list.png`: searchable list beside the document preview.
- `payment-entry.png`, `delivery-entry.png`: existing operations paired with the invoice before the operation; these previews are not presented as posted payment/delivery results.
- `quotation-grid.png`, `quotation-compact.png`, `invoice-grid.png`, `invoice-compact.png`, `delivery-grid.png`: existing renderer on a 612 × 792 point letter sheet with the application's current print margins.

The offline capture harness is `tmp/document-studio/PreviewDocumentStudio.java`. It mounts real components with fictional records and injects renderer output; it does not connect to a live store or post transactions. Swing table headers and responsive layout are laid out explicitly for offscreen captures. The main fixture uses Harbour Supplies and a 45,000 total; image content is illustrative, not production evidence. Latest captures use the isolated compiled classes in `SmartStock/target/document-studio-validation/classes` to avoid another build modifying shared outputs.

## Visual review

- Typography: minimum 14px logical control text in the workspaces, readable table rows, stronger total and title hierarchy. Document typography remains the existing print template; fit-width and 100/125/150 percent zoom are available for reading fine print.
- Layout: editing/detail controls stay on the left, a draggable divider separates the preview on wide displays. Below 1120 logical pixels, an Editor / Details and Document preview switch preserves useful form widths. Form content scrolls and action rows wrap; custom item actions remain outside the scrolling form.
- Paper and images: the sheet always retains US Letter proportions. Previous/Next navigation uses the renderer's explicit page boundaries. The preview and printer share the same Printable and letter margins. Letterhead images prepare off the event thread. The paper stays white in the app's theme.
- Palette: Deckers orange primary actions with dark text, purple selection, and shared surface/text colors. Native controls replace invented mockup decorations. No replacement logo or decorative imagery was added.
- Content: internal IDs are hidden from document and quotation-line tables. Literal search safely handles punctuation. Actual saved document data and location-specific branding come from the authenticated store API. Unsaved previews are marked and have no print action; saving still validates prices and approvals.
- Intentional adaptations: existing module navigation and operations are retained. Invoices are paid and fulfilled through their existing operations, not edited through the mockup's fictitious Save Draft action. Delivery notes retain their existing delivered/remaining quantity and signature layout; Compact remains a quotation/invoice option, not an invented delivery template.

## Corrections during verification

Small default controls were raised to readable sizes, collapsed notes fields were given wrapping content, internal product IDs were removed, toolbar wrapping was made height-aware, and small-screen mode was added. Saved customer identity is retained while asynchronous edit data loads. The editor total comes from server calculations, including customer discounts and VAT. Page changes/clearing prevent printing an old document. Opening a larger preview retains the selected template, and preview cache keys distinguish templates.

## Validation and limits

The isolated full Maven suite passed: 1,640 tests, 0 failures/errors and 210 skips. The final focused run after the optional-row and hidden-ID fixes passed all 25 tests with no failures/errors/skips. Security checks and full-tree whitespace checks passed. LetterDocumentViewTest specifically covers paper proportions, page boundaries, navigation/clearing, literal search, responsive view transitions, and stored document identity after columns are hidden/reordered and rows are sorted. The first shared-output runs were invalidated by concurrent compilation; the first isolated run had no assertion failures but one unrelated missing storefront manifest during regeneration. That file subsequently returned and its test passed on rerun.

Installed Windows application behavior, real store connectivity, operating-system DPI settings, physical printer output/margins and hardware behavior remain separate manual acceptance checks. No live payment/delivery, service restart, package publication, release-metadata change, or installed-app update was performed.

No unresolved P0/P1/P2 visual issues were observed in the final inspected renders. Native font antialiasing and printer-driver behavior can vary by installation.

## Correction in progress
The user rejected the previous visual result. Its green functional checks did not establish visual fidelity. The original third mockup is the authoritative layout reference; correction renders are under target/document-studio-correction/renders. No corrected visual pass is claimed yet.
