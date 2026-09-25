# CT221B order label printing

SmartStock prints custom order labels through the installed CT221B system printer
driver. Each copy is a separate label page containing the order number, Code 128
barcode, customer name, and due date.

1. Install the manufacturer's CT221B driver from https://ga.ctaiot.com/pc and
   connect the printer by USB. Confirm it appears in the operating system's printers.
2. In the driver's Printing Preferences, set the paper width and height to match
   the loaded roll and configure its gap or black-mark media setting. Print a driver
   test page. The paper width must be 25–54 mm.
3. In SmartStock Hardware Settings, refresh printers, select the CT221B queue,
   and click **Add Selected**. Keep `CT221B` in the system printer or POS name so
   SmartStock recognizes the model.
4. Select that printer, click **Set Order Label Default**, and **Save**. Configure
   your receipt printer separately as the receipt default.
5. Open a custom order slip preview and print the slip. Enter one copy when prompted
   for the number of order labels; labels go to the configured order label printer.

CT221B labels use the driver's configured stock size, with content centered within
the printable area and a maximum 48 mm print width. The barcode retains its aspect
ratio. An A4 or Letter driver default produces an actionable paper-size error.
The POS printer Format column controls receipts and slips; it does not change the
CT221B label size.

Direct Bluetooth printing is not implemented. Use a working system driver queue.
Physical verification is required: check alignment, gap feeding, a barcode scan,
and multiple copies on the actual roll before using it for live orders.

Manufacturer reference: https://imagepc.ctaiot.com/global_ctaiot/v2/pdf2/CT221B%20User%20Manual.pdf

## Two-across price tags

In Company Preferences → Price Tag Template, set the individual sticker width to
**0.5 inch**, height to **1 inch**, and **Labels across** to **2**. Set **Gap between
labels** to the measured space between the two stickers, then save the template.
Refresh preview to see the complete row. With no gap, a row is 1 inch wide by
1 inch high; with a gap its width is 1 inch plus that gap.

Set **Gap between rows (inches)** to the measured vertical gap and save it with
the template. SmartStock requests a page/feed pitch of sticker height plus this
gap, leaving the gap area blank. For 1-inch-high stickers with a 0.1-inch row gap,
each printed row requests 1.1 inches of feed. The individual sticker design stays
1 inch high. Existing templates default to zero row gap.

For a configured CT221B queue, SmartStock uses the printer's native TSPL path. It
sets the printable row size, selects transmissive gap/notch media with `GAP`, and
runs `GAPDETECT` using the template's label length and row-gap measurements before
printing. This supports backing stock whose registration reference is an oval hole.
Other label-printer models continue through their Windows drivers.

Price Tag Printing and configured label-printer printing fill each row from left
to right in cart order. Three stickers use two rows, with the final right-hand
position blank. Quantities count individual stickers. Receipt quick printing
continues to print one temporary tag at a time.

Set the system driver's stock size and feed mode to match the complete row and
loaded backing paper. Test alignment and barcode readability on the physical roll;
very narrow labels may require fewer visible fields or a simpler template layout.

## Portrait labels and rotation

Use **Portrait** or **Landscape** beside the individual sticker dimensions. The
editor follows that sticker's physical aspect ratio. Select an element and choose
0, 90, 180, or 270 degrees; rotations are saved with its layout. The element box is
its final footprint on the label, and its contents rotate inside that box.

For the photographed paired stock, Template 2 was repaired to 0.5 × 1 inch per
sticker, two across, zero gap within each pair, and 10 mm (0.3937008 inch) between
pairs. This interpretation places the large gap along the feed direction. The
compact design includes a name, price, vertical barcode, and code. It requires the
updated renderer in 1.0.189. Refresh or reopen preferences after updating to load
the saved template.

Printing uses the template's physical dimensions and does not stretch the row to
the driver's imageable area. Barcodes that exceed their boxes produce an error
instead of being cropped. Confirm actual stock alignment and scan the printed
barcode before printing a batch; the CT221B was not connected during development.
