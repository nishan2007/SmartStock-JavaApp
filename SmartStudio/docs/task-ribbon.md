# SmartStudio task ribbon

Add page and Delete page are visible above page thumbnails and together under Insert → Pages. Delete confirms removal and is disabled for the final remaining page; Add is disabled at 500 pages. Page changes update thumbnails, save with the template and support undo/redo. Ordinary card/label/banner pages start blank without generating data columns; bill-book pages retain their automatic numbering workflow.

Text controls share live font/size state between Home and Text format. Font style lists the real faces reported by installed/shared fonts (such as Regular, Light, Bold and Italic when present), and stores the chosen face with the template. Explicit typeface, style or size changes turn imported fitted text into ordinary sized text so the requested font/points take effect.

Layout's **Layers** button opens a movable, nonmodal floating list for card/publication editors. The list shows the frontmost layer first, includes type and visibility icons, and scrolls independently for large templates. Click a row to select its artwork, click its eye (or press Space) to hide/show it, and use the footer arrows or trash button to reorder/delete it. Hidden layers remain in the template but are excluded from canvas hit testing, preview, printing and PDF output. Visibility participates in undo/redo and persists on save; older files default to visible. Close or Escape dismisses the panel while editing continues in the canvas.

Measurement controls default to inches: template dimensions, paper dimensions, margins, gaps, picture/barcode sizes and text-outline widths. Existing publication dimensions and print settings convert at the UI boundary; physical layout and file dimensions stay unchanged. Font sizes remain in points. Opening a publication starts its size display in inches, with the unit selector available for other units.

Implemented in source on 23 October 2026. No release version, publication, installer or live store was changed.

The shared editors now use Home, Insert, Layout, Data and View tabs, with applicable Riso/Numbering tools and selection-specific Text format, Picture format and Code format tabs. Context tabs do not steal the active tab; losing an incompatible selection returns to Home. Home keeps template selection, insertion shortcuts, arrangement, text controls and undo/redo. Insert groups text/WordArt, images, codes, fields and pages. Source actions and enabled state are shared between shortcuts.

Save and Print stay outside the tabs. The workspace chooser is a 3 × 3 grid. Image Studio, Passport Pictures, Photo Queue and PDF Editor have their own task groups and persistent primary actions. The store branding remains on connection/login screens; the editing workspace uses a compact SmartStudio header.

## Compact layout

Card/publication editors place document width, height, units, artwork scaling, paper size and page layout directly in Layout. Print Setup contains margin and gap fields, copies, sheet selection, rotation, cut marks, preview and template-save actions. These tabs stay inline at compact sizes. Arrangement commands remain on Home. Optional Layers/Properties tools use task windows instead of reopening the side inspector in these editors.

Text format and Picture format contain the actual selection-bound controls directly in the ribbon. They wrap into readable rows and expand the ribbon when selected, including in compact mode. Home's Settings button opens the applicable formatting tab. Colour and recolour pickers remain individual actions; the text and picture formatting panels themselves do not open dialogs. Code formatting retains its existing settings window.

Below 1200 logical units of ribbon width, or a window height below 620, the ribbon initially collapses to preserve canvas space. Its tabs open bounded tool windows; Enter also opens the selected tab. Expand/Collapse provides an explicit per-workspace override. Card editors keep Add text/Add image and output actions directly accessible; Image Studio keeps source/process shortcuts. PDF and photo workflows retain their primary actions above their tabs.

Wide groups use labelled tool windows instead of horizontally scrolling command strips. Existing settings continue to apply live and their windows say so; Close/Escape closes the window, not an implicit rollback. Template/file/print operations retain their existing confirmations. Detailed tables, libraries and long settings can still scroll. Dialogs use the current monitor's usable bounds and retain a fixed Close action. Controls return to their original owner when a dialog closes.

## Additional editing commands

Print Setup is a dedicated ribbon tab, with a Save template setup shortcut. Publication template files retain their print setup; reopening cards, labels and bill books restores paper size (including custom sizes), independent margins/gaps, copies, rotation, crop marks, mixed-page layout and applicable numbering order. New documents initialise their own setup. Changing setup marks the document unsaved; use Save or Save template setup to persist it. Printer-driver selections remain native print-job settings, not portable template credentials or device configuration.

Align and Distribute operate on selected object boxes in badge and publication editors. Align requires two objects; equal horizontal/vertical gaps require three. They keep object size/content, update the existing document history, and report invalid selections. Insert → Merge field chooses an existing data column and appends its placeholder to selected text, or creates a new text object. It does not create a second data model.

## Extending the ribbon

The visual correction uses Studio-scoped warm-white (#FBF8F3), plum (#4D235B) and Deckers purple tokens, with Segoe UI text, icon tiles and short labels below ribbon commands. Save/Print use inline icons. Font Awesome Free 6.7.2 Solid is bundled under icons/ribbon with its licence; no network or icon download is required at runtime. The new Print Setup tab remains visible. The component-render tool produces fresh Home/Insert, monitor and compact screenshots for visual comparison. Existing document artwork is not replaced by the synthetic card shown in the design concepts.

StudioRibbon.add(tab, group, control) registers a control; task(...) registers a bounded settings destination. shortcut(...) invokes an existing AbstractButton and follows its enabled state. New commands should reuse the existing action/model and be placed in the appropriate group, with availability driven by existing capability/permission logic. Do not add empty tabs or placeholder Coming soon buttons. Shared Deckers colours and component styling remain the base; Studio-specific ribbon styling does not alter the POS theme.

## Validation

- Full SmartStudio suite: 260 tests, zero failures/errors, one skipped; includes shared shortcut state, six size/scale budgets, context tabs, arrangement geometry, formatting and workspace retention.
- Full SmartStock suite: 1,287 tests, zero failures/errors, 115 skipped.
- Headful dialog test: usable monitor bounds, 44-unit Close target, Escape dismissal and restoration of the original editor parent.
- Isolated full-window component renders: Home/Insert at 1366 × 768, 1920 × 1080 and a 910 × 469 logical compact window; workspace chooser and specialist panels. Six scale budgets are layout tests, not a claim of physical DPI acceptance on six Windows configurations.
- Repository security script through Git Bash and git diff --check passed. Additional targeted formatting/dialog/ribbon tests passed after the final colour-control sizing adjustment.

Reproduce component previews with SmartStudio/tools/PreviewRibbon.java using the classpaths documented in its header. Output and its synthetic development profile belong under target/. The preview does not authenticate or process live store images.

Physical printer output, paired live-server/model/phone workflows, installed-app behaviour, manual keyboard/accessibility review and physical Windows DPI configurations remain separate acceptance checks. No service, database, printer, drawer or NFC operation was performed for this redesign.
# Template data records

New data tables start without preset columns. Use **Data → Add field** to define fields, or **Fill number series** to create a numbered field. Saving a template stores its ordered fields and every record alongside its artwork and print setup, including empty fields and blank rows. Excel imports save the current values; reopening a template does not require the original workbook.

For example, save a ticket field containing 1–500, reopen that template, then use Fill number series with start 501 and 500 values to replace it with 501–1000. Save again to retain the new range. Opening a different template replaces the table with that template's fields and records. Older templates without a saved field list recover their fields from record keys.

Shared templates include saved records and follow the template's existing store access and backup permissions. The store server must include the updated template validation to accept templates containing records. Order associations remain excluded from reusable shared templates.


Fill number series includes Add leading zeros and a live preview. Minimum digits 0 matches the largest number automatically (001–100 or 0001–1000); enter 3 or 4 for a fixed minimum width. Reopening a padded run preserves its width. Generated numbers keep leading zeros in saved template records.

The repeated workspace heading is removed beneath the application bar. Ribbon tabs share their row with Save, Print and Export PDF, using rounded 44px buttons, purple icons and text for Save/Export PDF, and a purple gradient with white icon/text for Print. The tool area retains the full width; tabs wrap at narrower sizes.

Print Setup → Preview opens a visible, screen-bounded sheet-preview window using the current local design and print settings. It supports sheet navigation and Close/Escape without saving, server access or a printer dialog. Printing retains a separate Continue to printer action. Automatic preview refresh remains an internal render and never opens a modal window. Shared-template acknowledgement checks are retained; a mismatch now explains that the draft is unverified and recommends a local template copy before reopening/retrying, with store/Studio version compatibility identified as a possible cause.

Bill Books → Numbering exposes Print content directly: Full template + numbers, Numbers only (preprinted books), or Template only (no numbering), alongside the number-field names and Preview. These choices affect print/preview output without deleting artwork or number fields from the saved design. Numbering order, custom positions, position map and cut-and-stack also live directly in this ribbon; no numbering-settings popup or duplicated margin/paper rows remain.

### Template orientation and picture rotation

Layout → Orientation offers Portrait and Landscape. Changing it turns the whole design (including all publication pages, masters and guides) while preserving artwork proportions. It does not change the printer paper setup. Square templates remain square. Undo/redo and template files retain the result.

Picture format includes Left 90°, Right 90°, Rotate 180° and a Rotation ° field for custom absolute angles such as 30° or 45°. Presets turn relative to the current angle; positive angles turn clockwise around the picture centre. Controls operate on the selected picture and retain its size, asset and aspect ratio. Badge photo slots and artwork support saved rotation too.

Validation covers geometry round trips, badge rendered pixels, archive persistence, undo/redo, typed custom angles and ribbon bounds at 1366/1920 screen widths with 100%, 125% and 150% logical scaling budgets. Native Windows scaling and physical printing remain separate manual checks.

### Windows update handoff

In-app setup now starts from a detached Windows helper that waits up to two minutes for the initiating SmartStudio desktop process to exit. This avoids racing Inno Setup Restart Manager during JVM shutdown. The helper does not force-close applications, and writes failures to `smartstudio-update-handoff.log` beside the downloaded update. The installer still checks other applications holding files. The existing downloaded installer size/SHA-256 verification remains enforced. A real Windows process regression test verifies setup does not execute while the parent process is alive, including paths containing spaces/apostrophes. Live in-app update acceptance remains separate from this synthetic process check.

Passport Pictures → Home → Source now includes a visible **Photo Queue** button beside Choose photo. It opens the existing shared queue; select a row and choose **Use in Passport Pictures** to return to framing. Navigating to the queue leaves the current passport photo intact until another is chosen. The source action cancels a pending phone capture before navigation.

### Consistent Layers in Badge Cards

Badge Cards now opens the shared rounded floating Layers panel from Layout → Layers and View → Layers. The old docked Layers section is removed. The shared list scrolls for long templates and shows artwork names/type icons, visibility eyes, selection, move up/down and delete controls. Badge visibility persists in template files and undo/redo, and hidden elements are omitted from preview, print rendering and canvas hit testing. The floating window closes when its workspace is hidden. Business Cards, Labels, Banners and Bill Books already use this same panel.

### Shared template workspace contract

Badge Cards, Business Cards, Labels, Banners and Bill Books use Home, Insert, Layout, Print Setup and View in a consistent order, with selection-bound Text/Picture format tabs. Save, Print and Export PDF share header styling and order. Layout exposes Layers, Orientation, Width, Height, Units and Apply size; the shared field component keeps labels, control heights and spacing consistent. Dimensions default to inches. Layers opens the same floating visibility/reorder/delete list from Layout and View. Formatting uses the same text/picture editors, including font faces, transparency and rotation.

Badge Cards now uses task dialogs instead of the old docked inspectors, places person/record details under Data, and exposes direct dimensions, scaling, zoom and duplex/back-template settings. Badge single-card/front-and-back workflows remain distinct from publication page/merge/numbering tools. Banner Print Setup exposes its actual-size driver workflow, PDF and save controls without sheet-tiling settings. Existing specialized workflows and permissions remain intact. Cross-workspace checks cover common tabs/actions and layout bounds at 1366/1920 widths and 100/125/150% logical display budgets. Physical printer and manual native scaling checks remain separate.

### Workspace chooser visual parity

The plain text workspace buttons are replaced with icon-led cards following the report: nine named workspaces, a short two-line purpose description, white rounded surfaces, lavender icon tiles, subtle borders and purple hover/keyboard focus. All icons come from the existing bundled Font Awesome font. Cards retain normal button keyboard activation and accessible names/descriptions. The grid adapts from three to two/one columns and tracks the viewport width. Component rendering, every workspace callback and label fit are checked at 1366/1920 widths with 100/125/150% logical budgets; native manual DPI remains separate. Preview evidence: `SmartStudio/target/workspace-chooser-redesign.png`.

### Organized settings ribbons

Layout, Print Setup, Text format and Picture format now use a shared semantic-band layout. Bounded rounded groups have consistent captions, aligned measurements, readable labels and icon actions. Template size/paper/page actions, margins/spacing/output, font/emphasis/outline and picture size/appearance/rotation each have separate visual groups. Controls retain their original ownership, listeners, models and selection bindings. The ribbon measures its content and wraps groups on narrower displays instead of clipping settings or adding a control scrollbar; the canvas retains the remaining space. No settings are moved back into a side panel.

Component bounds checks cover both target screen widths at 100/125/150% logical budgets, with existing formatting/edit/undo tests retained. Native component previews are in `SmartStudio/target/refined-ribbon` and `picture-rotation-ribbon.png`. The chooser icon role is preserved during shared style application, with a style-pass regression check. Native Windows scaling and printer output remain separate verification steps.

### File backstage implementation

Template editors now have File before Home; Home remains the initial tab. File replaces the canvas with the approved navigation rail, form/document cards and fixed action footer; it hides editing output actions and the old workspace footer. Home/Escape/Back restores the canvas. Supported commands: File info, New, Open, recent local files in this session, Save, Save As, Rename, artwork import, supported exports, print setup/printing and Close. Ctrl+Shift+S opens Save As. File state is shared by Badge Cards and the publication editor modes (including Vinyl Cut).

Save As creates and activates a new local or shared template, preserving the source. Existing local targets require replacement confirmation and the active source cannot be overwritten as a “copy”. Shared copies get new server identities through the existing save endpoint. Shared saves/renames retain permission, expected-hash and acknowledgement verification. Cached duplicate names are rejected before shared requests; server checks remain authoritative. Rename moves a local file without modifying its bytes, keeping the content title independent, and checks fingerprint/conflicting names. Shared rename changes the template label while retaining its server identity. Invalid names and reserved Windows paths are rejected. Local badge saves continue targeting the chosen local copy even when connected to a shared library.

File info uses actual document metadata; Recent is deliberately in-session and cleared on sign-out, without persisting profile paths. New/Open/Close offer Save/Discard/Cancel and wait for asynchronous save completion before proceeding. Failures leave work open. Native formats/exports, record retention and specialized print workflows remain workspace-specific. Existing template libraries/open-file pickers supply the Open flow. Preview evidence: `SmartStudio/target/file-view.png`. Native component render/navigation and local-copy/rename tests are automated; paired-store shared writes and physical printers remain separate acceptance checks.
