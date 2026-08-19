# Epson receipt printer implementation

SmartStock builds receipt data only after the LAN checkout API has committed the sale.
ReceiptPrinter routes 40-column receipts to EpsonReceiptPrintService, which submits raw ESC/POS bytes to the
explicit Windows queue saved in Workstation Preferences. Receipt formatting remains in ReceiptFormatter;
drawer and cutter commands are appended by the hardware service.

Epson settings are workstation-local in %USERPROFILE%/.smartstock/hardware.properties. Existing installations
remain compatible and retain automatic cutting. Once Epson controls are enabled, the saved cutter and drawer
options apply. Cash checkout may pulse the drawer; preview/history reprints never do.

The Windows queue accepting a job proves only that it was submitted, not that paper physically printed. Verify the
exact Epson model, official driver, 80 mm paper, cutter, drawer cable/pin, offline behavior, and installed application
on the register before enabling drawer support.
