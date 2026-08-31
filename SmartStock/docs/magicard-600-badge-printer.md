# Magicard 600 Duo badge printer

SmartStock prints employee badges through the official Magicard Windows driver. It does not send raw data to the printer, install drivers, or store the printer IP address in the shared database.

## Windows and network setup

1. Give the printer a stable address with a DHCP reservation or a static network configuration.
2. Confirm the printer LCD identifies the unit as **Duo**. Automatic front-and-back printing is unavailable on an Uno unit.
3. Download and install the current Magicard Windows driver from [Magicard support](https://support.magicard.com/support-solution/windows-driver/). Installation requires local administrator rights.
4. Create the Windows printer queue for the Magicard 600 Ethernet address.
5. Open the queue's printing preferences, confirm the printer is detected as a Duo, enable two-sided printing, select CR80/ISO ID-1 card media, and print a Windows test card.

## SmartStock setup

1. Open **Workstation Preferences > Hardware Settings**.
2. Refresh installed printers.
3. In **Badge printer (Magicard 600)**, enable badge printing and select the Magicard Windows queue.
4. Leave **Magicard 600 Duo** and **Show print dialog** enabled, then save.
5. Click **Test Badge** and confirm that one card contains both sides in the correct orientation.

The setting is saved only for the current workstation in `.smartstock/hardware.properties`. If the saved queue is renamed, removed, or unavailable, SmartStock stops before printing and asks the operator to restore or reselect it.

## Physical acceptance checks

- Print front-only and back-only jobs and confirm each produces one correctly oriented side.
- Print a front-and-back job and confirm the Duo produces one physical card, not two cards.
- Check edge clipping, color, photo quality, small text, and barcode readability.
- Cancel the print dialog and confirm the employee badge print count does not change.
- Check the messages shown when the queue is removed, the printer is offline, or cards/ribbon are unavailable.

Magicard documents the printer's Ethernet and optional duplex capabilities on the [Magicard 600 product page](https://magicard.com/printers-and-software/magicard-600/). If duplex is unavailable in the driver despite a Duo display, follow [Magicard's duplex troubleshooting guidance](https://support.magicard.com/support-solution/duplex-printing-is-grayed-out/).
