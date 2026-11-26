# Business-card clipping investigation — 4 November 2026

The local 0.1.96 C5240 print report recorded a Letter sheet with a printable
rectangle of 25.4,25.4 / 165.1,228.6 mm. This inserts one-inch margins on every
edge. Earlier C5240 reports recorded 6.35,4.233 / 203.2,270.933 mm.

The 0.1.87 release notes document the physically confirmed original fix:
Windows raster printing, original-resolution artwork and supported 600-DPI
colour output. PublicationPrinting, its printable implementation and PrintMargins
have identical class bytes in the retained 0.1.87 and 0.1.96 JARs. The new report
also confirms the raster pipeline is active. The original fix was retained;
the available evidence does not identify a release that first caused the new
print-dialog rectangle.

After the native dialog, resolve the selected printer's supported printable area
using the final media/tray attributes. Do not carry the dialog's inherited area
into the capability query. Publication margins continue to position the cards;
the printer rectangle must not add an unrelated software clip. Retain actual
card dimensions, raster printing, image resolution and explicit quality choices.

Regression coverage supplies the observed one-inch rectangle, verifies it is
excluded from the capability query, and checks replacement with the selected
printer's supported area. Existing tests cover full-size card placement, raster
bands, crop marks and original 600-DPI detail.

Physical C5240 output and the installed application still require verification
with the corrected build. No release publication or live installation is part
of this source correction.
