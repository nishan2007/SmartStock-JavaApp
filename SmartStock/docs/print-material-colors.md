# Print material colors

Print Materials has an optional Colors field. Enter named swatches separated by
commas, for example `Black=#000000, Red=#ff0000, Gold=#d4af37`. The table shows
the saved list and selecting a material restores it for editing.

A nonempty list restricts designer elements using that material to one of its
stock colors. Leave it blank for varying artwork colors, such as transfer paper
or embroidery. Eligibility follows the saved list, never the material's name.
All presets belonging to a material inherit its colors. The local server checks
both the print mode and selected color when calculating or submitting a design;
stale drafts must select a current color or add-on again.

The local baseline and ordered migration add `available_colors` as a JSON object
on `custom_order_print_materials`. SchemaContractService applies the upgrade to
existing profiles; installers and server provisioning use that same contract.
The existing reference/mirror paths already include this table and serialize
its columns dynamically, so the new column travels with the material row. No
new lookup predicate or database index is needed. Older material-save clients
that omit the field preserve an existing palette.

Checks cover palette parsing, duplicate/invalid colors, designer mode selection,
and server rejection of unavailable colors or forged print modes. The disposable
database test also covers upgrade idempotency and material save/load. Installed
Swing behavior, live database upgrades, and LAN clients require deployment
verification; automated checks do not verify a live store.
