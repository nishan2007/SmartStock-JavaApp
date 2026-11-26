# Overseas Orders design QA

final result: passed

## Evidence and comparison method

The selected targets are `reference-list.png`, `reference-detail.png`,
`reference-quote.png` and `reference-item.png` in this directory. The matching
native captures are `rendered-*.png`. All four were opened and compared as paired
images, not judged from source code or a separate remembered image.

Each reference is 1488 × 1058. Its simulated 44-pixel Windows title bar was
excluded. Native content was captured at 1488 × 1014 with Java UI scale 1, the
Windows system look and feel, and SmartStock's theme pass. No application content
was cropped out. `compare-previews.ps1` produces paired comparisons in
`target/overseas-design/compare-*.png` after running the preview command in README.
The payment summary and Add item fields were also examined in focused paired
crops (`focus-detail.png` and `focus-item.png` in the same capture directory).

## Findings resolved during iteration

- **P1: quote item viewport collapsed under the Windows theme.** GridBagLayout
  used minimum heights when a wide field's preferred width exceeded its column.
  Replaced vertical form stacks with a layout that retains each row's preferred
  height. Recaptured all three quote rows and added a native-theme regression test.
- **P2: off-screen summary at a smaller register size.** Pages now track viewport
  width and move the summary below the form below 1100 pixels. Verified 1024 × 724
  content captures and a test asserting no outer horizontal scrollbar and a
  reachable payment control.
- **P2: stretched controls and incorrect column proportions.** Buttons retain
  their natural height, the customer lookup aligns with the fields, and main
  columns follow the selected list/detail/quote/item layouts. The list toolbar is
  capped at the mockup's width rather than stretching its search field across the
  entire window. The quote summary fills the form's height.
- **P2: permanent empty status space clipped the Add item footer.** Blank status
  messages no longer reserve vertical space. The optional purchase cost control
  and Add/Cancel actions are visible at the reference size.
- **P2: original dense command grid and raw event log.** Replaced with the
  selected tabbed detail screen and More actions menu. New quote and Add item are
  full workspace pages, and the orders list is a separate main page.

No unresolved P0/P1/P2 visual or core interaction findings remain in these states.

## Required fidelity surfaces

| Surface | Result |
| --- | --- |
| Typography | Segoe UI native text; distinct title, section, body and helper hierarchy; readable table headings, right-aligned GYD amounts and live totals. Native text and font-icon metrics differ slightly from the raster mockups. |
| Spacing/layout | Four selected page structures, broad forms, compact customer header, collapsed optional details, separate list, selected-row actions and persistent summary. Same-viewport captures checked for clipping; narrower pages scroll vertically. |
| Colors/tokens | Existing Deckers orange primary actions, purple tabs/search/selection/totals, pale selection fills, white surfaces, shared slate background/text/borders. Logo retains its orange/lime/yellow/magenta brand colors. |
| Images/assets | Real existing Deckers logo, scaled without distortion; existing licensed native icon font. Screenshot preview uses the selected file, without invented product photography. |
| Copy/content | Mockup titles, labels, controls, customer and illustrative GYD totals retained. Optional purchase cost and delivery details remain collapsed. Operational values come from the server. |

## Expected native/runtime differences

- Standard Windows field, dropdown, scrollbar, focus and icon rendering is kept.
  The shared native controls have slightly squarer corners and lighter text than
  the generated images; subtle raster gradients are represented by shared solid
  palette surfaces. These are P3 polish differences, not hidden controls or
  changes to the four-page information hierarchy.
- Shipment markers reflect real quantities. The reference inconsistently shows
  Ordered completed while all lines are Processing; the implementation does not
  manufacture that completed state.
- Clipboard paste remains available beside Choose screenshot. Status messages
  appear only when needed. These retain existing working capabilities.

## Verification scope

Native interaction tests exercise list → detail → list, preserved search and
selection, quote → item → quote, grouped-currency entry and live totals, invalid
item validation, screenshot upload → reference save → issue with current revision
and unique request IDs, server permission-driven disabled actions, compact layout,
and the Windows-theme item viewport regression. The preview uses a mock transport
and does not access a live database or physical printer.

Real store payment, drawer, printer, NFC, Windows service and installed-app checks
are separate deployment checks. No release was published or installed for this
UI change.

## Final implementation checklist

- [x] Orders list, selected order, new quote and Add item match the selected page structures.
- [x] Native Windows rendering inspected at reference and compact viewport sizes.
- [x] Full Maven test gate passed after the layout fixes.
- [x] Seven native workflow tests passed with headless mode disabled (none skipped).
- [x] Repository security check and `git diff --check` passed.
- [x] Existing server authorization, accounting and receipt workflows preserved.
