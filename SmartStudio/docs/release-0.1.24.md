# SmartStudio 0.1.24 — Corsiva and Publisher outline correction

The shared publication font resolver recognises the downloaded
"Monotype Corsiva Regular" naming variant when a source requests
"Monotype Corsiva". Source font names are retained in archives. The same resolver
is used for ordinary text, fitted imported lettering, WordArt and missing-font
diagnostics. Other typefaces are not silently replaced.

Publisher primary and tertiary boolean properties now merge by their declared
high-word use masks. Previously, a tertiary property could erase an explicit
disabled-outline flag, exposing an unwanted green outline on the foreground
Asian Spices title. Outline strokes use rounded joins to prevent spikes around
the lettering. The updated preview was visually checked against the saved
Publisher reference. Exact rendering of every original envelope is not certified.

All 561 Publisher files in the local Deckers Skeldon folder were audited without
parser errors. Each contains an embedded saved preview. Explicit character runs
and native WordArt reference 65 absent font names across 331 files. Font-table
entries with no explicit text reference were excluded. Inherited/default styles,
linked resources and raster lettering require separate visual inspection.
Bill books and other publications outside the card/label library were audited,
not imported as business cards. The local per-file and per-font TSV reports
are saved outside the repository.

The user supplied two font ZIPs. Eight TTF files were validated and installed
in that Windows user's font directory. No font files are redistributed in the
application package. Original Publisher documents remain unchanged; affected
local imports are refreshed with prior archives backed up separately.

Validation: all 70 SmartStudio tests passed; targeted shared publication,
WordArt and font-resolution tests passed. The boolean-mask regression verifies
that later flags preserve both enabled and disabled outline settings. Security
and whitespace checks passed. Packaged and installed native startup checks
passed, and the installed shared renderer matches its build input. SmartStock
was not restarted or installed by this work. This local build is unpublished.

Portable ZIP: SmartStudio-0.1.24-windows-20261016-184853.zip, 234088862 bytes.
SHA-256: 135b918bcb77033517d2997e2fc8eef1469d3292b1c82e7453a665cb1b0f3014.
Installed main JAR SHA-256:
b0348fb4c250fe5935b01bcd0ed4301a2f4a31e8218a9607f28542d21059302f.
