# Labels workspace

Choose Labels to open the searchable template library. Select a design to preview its pages, dimensions and editable-text count, then Open selected. Template & insert > Template library reopens the list. Save to template library saves reusable designs without merge records.

New labels start at 50.8 × 25.4 mm. Each .sspub template retains its own dimensions. The Windows local importer accepts Publisher .pub, PowerPoint .pptx/.pptm and Draw .odg sources through LibreOffice's Python/UNO runtime. Macros and link updates are disabled. Word and Photoshop files are not supported by this importer.

Source text, recovered WordArt wording, individual table cells and artwork are separate selectable objects. Drag them or use Layers/Properties to edit position and dimensions. Double-click text to edit; Ctrl+Enter commits it. Font family, rotation, colour and simple-shape fill/stroke are available in Properties. Table cells retain their row, column and merged spans while remaining separately movable. Complex artwork remains an image layer. Picture-only lettering cannot be edited as text and is flagged by the picker. Exact Publisher WordArt envelopes and substituted fonts require visual review.

Generate QR code opens the generator with QR selected and a square size. Enter a URL, text or {{merge_field}}, then confirm to add an editable QR layer. Use Edit barcode / QR or the selected layer toolbar to update its value and size.

Generate barcode / QR creates a barcode from editable data. Edit barcode / QR changes the selected generated object. Replace image with barcode replaces a selected barcode picture at its existing bounds using known data. Supported types are Code 128, Code 39, EAN-13, EAN-8, UPC-A, ITF and QR. {{merge_field}} values can be used; invalid or missing data prevents output. Imported barcode pictures are replaced automatically only when safely decoded as an individual barcode.

Template size accepts mm, cm or inches. Optional scaling changes artwork as well as canvas size. Print setup controls sheet dimensions, copies, margins, gaps, rotation and PDF output separately. Verify actual-size printing, margins, cutting and physical barcode scanning on the intended equipment.

The signed-in library is shared through the SmartStock server, with a local offline cache and verified OneDrive recovery backups. Existing libraries migrate automatically after both applications are updated. See [Shared template library](shared-template-library.md). Original source files are unchanged. Local transfer verification and limitations are recorded in release-0.1.21.md.
