# SmartStudio 0.1.23 — Publisher appearance corrections

The Asian Spices / 30 GRAMS BIKA example revealed that the previous import
recovered fields without preserving their original stack order. This could
expose text hidden behind the original background and retain empty WordArt
placeholder polygons as visible blobs.

The importer now exports original shape and ancestor-group geometry. Native
WordArt replaces its complete matching placeholder group once, at the original
stack position. It no longer matches unrelated nearby rectangles or moves all
text above all artwork. Publisher palette references resolve through the actual
document palette, and paragraph separators retain the intended line count.
Native outline enable flags and widths are respected.

The cylinder panel in the example is now an editable CYLINDER object with a
filled top. LibreOffice had converted the cap as a hole. Source WordArt outline
geometry is stored independently from user-created inset effects.

The template picker reports missing source fonts rather than implying visual
fidelity. This computer lacks Monotype Corsiva and Bookman Old Style used by
the supplied Asian Spices source. Its saved Publisher thumbnail provides an
independent appearance reference, but is too low-resolution to substitute for
the editable print design. Exact logo/typeface matching remains unverified and
requires those licensed fonts. No raster screenshot is substituted for editable
lettering.

Verification: all 69 SmartStudio tests passed, including two new source-order,
palette and filled-cylinder regressions. Targeted shared publishing and WordArt
tests passed. Security and whitespace checks passed. The local app image and
installed 0.1.23 launcher passed startup; installed shared dependency matches
SmartStock 1.1.36. The portable ZIP is 234087662 bytes, SHA-256
24e3e3f417010dda07ab11788f195d234b9012b9ac86f3301f335870dd7327d2.

Source Publisher files remain unchanged. Previous template archives are retained
under the user's .smartstudio/template-library-before-accuracy folder. The local
library was rebuilt from source: 28 Publisher label files and 69 Publisher
business-card files reimported without failures. The retained PowerPoint imports
bring the library to 114 templates. All 298 label pages and 104 business-card
pages reopened and rendered successfully. This is technical validation, not a
claim that all 402 pages visually match Publisher. Missing-font reports are saved
outside the repository; 31 label templates reference absent fonts.
Metadata/model changes are not required.
SmartStock was not installed or restarted by this correction work. This build
has not been published as a visually certified replacement.
