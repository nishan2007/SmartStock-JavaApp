# Epson receipt printer implementation

SmartStock builds receipt data only after the LAN checkout API has committed the sale.
ReceiptPrinter routes 40-column receipts to EpsonReceiptPrintService, which submits raw ESC/POS bytes to the
explicit Windows queue saved in Workstation Preferences, or directly to an enabled raw TCP endpoint. Receipt formatting remains in ReceiptFormatter;
drawer and cutter commands are appended by the hardware service.

Epson settings are workstation-local in %USERPROFILE%/.smartstock/hardware.properties. Existing installations
remain compatible and retain automatic cutting. Once Epson controls are enabled, the saved cutter and drawer
options apply. Cash checkout may pulse the drawer; preview/history reprints never do.

The Windows queue accepting a job proves only that it was submitted, not that paper physically printed. Verify the
exact Epson model, official driver, 80 mm paper, cutter, drawer cable/pin, offline behavior, and installed application
on the register before enabling drawer support.

## Native Ethernet ESC/POS

Workstation Preferences can enable a raw TCP transport for 40-column ESC/POS output. The commissioned NS8360L
defaults are `10.1.1.23` and port `9100`; native Ethernet remains opt-in so upgrades do not redirect existing
Windows-queue installations. When enabled, SmartStock sends the complete ESC/POS byte stream directly to the
printer and leaves all Windows print queues intact. Letter-size printing and dedicated label-printer output continue
to use their configured Windows queues. A successful socket write confirms delivery to the printer endpoint, not
physical paper output, so cutter, drawer, paper, and offline behavior still require installed-workstation checks.
