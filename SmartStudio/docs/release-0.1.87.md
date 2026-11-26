# SmartStudio 0.1.87 Windows update

Build 1087 selects the Windows native raster printing pipeline at startup before
PrinterJob initializes. Direct publication painting retains original image pixels
and text until the printer-resolution raster bands; the intermediate 300-DPI
sheet image is removed. Requests supported colour, 600-DPI resolution and high
quality when not explicitly specified, preserving user-selected attributes.
Paper size, tray and duplex choices remain native printer-dialog choices.

Physical evidence: the C5240's Generic Plus PCL6 driver cropped output from the
normal Windows drawing-command pipeline. The forced-raster test printed all 12
Evergreen cards without cropping, but its 300-DPI sheet was blurry. Rendering
original artwork directly with bicubic interpolation and 600-DPI colour produced
the subsequent Letter plain-paper test, which the user confirmed was good.
No PDF reader is involved in this direct printing path.

- Updater: `SmartStudio-update-0.1.87.exe`, 173,051,057 bytes.
- SHA-256: `022884224c21367dde5e2d770ecd85cf563c714ac1af72cdeedfdc81340d09c9`.
- Exact SmartStock 1.1.91 dependency retained.
- Maven test/package: 336 tests, zero failures/errors, one skip.
- Regression coverage checks fine 600-DPI image detail, native pipeline startup,
  hardware margins, repeated/banded callbacks and explicit quality choices.
- Security and whitespace checks passed.

Acceptance evidence: `D:/SmartStock-studio-0.1.87`.
Isolated 0.1.86-to-0.1.87 update, exact installed JAR identities, runtime
preservation, native startup, model exclusions and notices checks passed.
The confirmed high-quality C5240 test used normal Letter paper. High-quality
Heavy 2 bypass output and other hardware remain separate verification steps.
No live store installation or macOS build was performed.

Published to the Studio Windows update feed on 27 October 2026 after complete
hosted download, size and SHA-256 verification. Independent feed verification
passed for version 0.1.87, build 1087, artifact identity, size and published status.
