# Business Cards

SmartStudio's Business Cards tab creates exact 3.5 × 2 inch (88.9 × 50.8 mm)
cards using the shared Publisher document, renderer and sheet-packing engine.

- Edit text, position, size, font, bold and colour in the layer table. Drag layers
  in the card preview. Add reusable artwork with Add artwork.
- Open a multi-page `.sspub` Publisher template at the same card size. Save
  template writes the design without personal merge records. Native `.pub`
  files can be opened directly using a local LibreOffice converter. SmartStudio
  detects common installations and remembers a manually selected executable.
  Imported templates retain their original size, including portrait/custom cards.
  The Windows editable importer uses LibreOffice's bundled Python/UNO runtime.
  Source text, recovered WordArt wording, table cells and artwork are separate
  objects. Text and cells can be moved and edited independently. Simple shapes
  retain editable fill/stroke properties; complex artwork remains an image layer.
  Picture-only originals have no separate text to recover and are identified in
  the picker. Review fonts, WordArt curvature, geometry and every page before printing.
- Add records in the built-in table. Add field creates another named column;
  use `{{column_name}}` in template text. Select a row to preview its card.
- Use the page selector to edit each page. Add page creates another card design;
  Delete page removes the selected design while retaining its table columns.
  Page number field gives a page its own merge column, such as `page1_number`
  or `page2_number`, and inserts the matching text placeholder. New pages include
  this field automatically. Columns remain independent: page 1 can use numbers
  starting at 1 while page 2 uses numbers starting at 1001.
- Fill number series fills an existing or new column using a starting number,
  step, number of values, starting table row and optional minimum digits.
  For example start 10, step 5, count 3, minimum digits 4 gives 0010, 0015, 0020.
  Negative and decimal steps are supported. Other columns remain intact, and
  replacing populated cells requires confirmation.
- Connect Excel supports `.xlsx` and `.xls`, with worksheet selection. The first
  populated row must contain unique field names using letters, digits and
  underscores, starting with a letter. Empty data rows are ignored. Files are
  read locally, without modifying Excel. Formula cells use their saved results;
  recalculate and save in Excel before reloading.
- Reload Excel replaces the table with the current saved worksheet after a
  confirmation. Table edits are temporary and never written back to Excel.
- The `quantity` column controls copies per record (blank means one). Without
  records, Copies controls the print quantity for a design with literal text.
  Remove unused merge placeholders before output; all referenced fields must
  have values in every record.
- Choose Letter, A4, Legal, 11 × 17 or Custom. Custom width and height are in
  inches; margins and gaps remain in millimetres. The complete sheet fits the
  preview panel automatically, including margins and cut marks. Changing the
  size recalculates the card packing. Saved templates retain their sheet size.
  Rotation packs
  more cards when possible without resizing. Preview shows the selected sheet;
  printing and PDF export include all sheets. Quarter-inch margins and no gap
  fit 12 rotated cards on Letter paper, or 10 with rotation disabled.
- Each page on separate sheets packs copies of one template page per sheet.
  Mix pages on the same sheet places successive template pages in successive
  card positions for each record/copy, continuing on the next sheet when full.
  Both modes print every page at the original card size and use the same layout
  for preview, PDF and printing. For two pages and 13 merge records on a 12-card
  sheet, separate mode uses 4 sheets and mixed mode uses 3 sheets.
- Print exported PDFs at actual size / 100%. Check paper size, printable margins,
  physical 3.5 × 2 inch size and cut alignment on the actual printer.
- Print first opens a larger full-sheet preview with a sheet selector. Continue
  to printer opens the printer dialog. Select matching paper there; custom stock
  must also be configured in the printer driver. Unsupported paper dimensions
  or margins stop the job rather than changing the layout.

This tab supports single-sided cards. Merge data is cleared on sign-out and is
not saved by Save template. Export or print before closing if needed.

Shared storage and automatic migration for this category are described in [Shared template library](shared-template-library.md).
