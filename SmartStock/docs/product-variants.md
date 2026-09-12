# Product variants

## Item color

All inventory items have an optional **Color** field in New Item and Edit Item.
Color appears beside Size in Inventory and item search results and can be searched
alongside names, sizes, and barcodes. Ordinary item colors can also be edited
directly in the Inventory Color column.

For a group with a Color option, Manage variants controls each item's Color field.
Existing reviewed Color options are copied into the field during the local schema
upgrade (`20260910140000_inventory_item_color.sql`). When attaching existing items,
their colors prefill the Color option. Detaching a variant preserves its color.
Older registers that omit Color when saving an item preserve the stored value.
The field travels with the existing product row in catalog sync and recovery.

## Item flavor

All inventory items also have an optional **Flavor** field in New Item and Edit
Item. Flavor appears beside Color in Inventory and item search results, participates
in catalog search, and can be edited directly in the Inventory Flavor column.

For a group with a Flavor option, Manage variants controls each item's Flavor
field. Existing reviewed Flavor options are copied into the field during the local
schema upgrade (`20260911120000_inventory_item_flavor.sql`) without replacing an
existing nonblank value. Attaching an existing item prefills the option from its
Flavor field, and detaching a variant preserves its flavor. Older registers that
omit Flavor when saving an item preserve the stored value. Color and Flavor are
independent, so a product can use either or both.

## Existing items

In Inventory, check the items to combine and select **Group / manage variants...**.
Enter the main product name. Define options with names such as `Color; Size`, select
**Apply option columns**, and fill each row's values. Existing sizes are prefilled
when the option is named Size. Review the original identifiers, pictures, prices,
and quantities before saving.

Size, Color, and Flavor are standard separators. Each variant must fill at least
one of those three fields, while the other two may remain blank. Any custom option
defined outside those standard separators remains required for every variant.

To rename the main item, select its group or a member, open **Group / manage
variants...**, change **Main product name**, and save. The same dialog provides
**Main primary barcode** and **Main additional barcodes (one per line)**. These
are optional and must be different from all individual item and other group
barcodes. Edit Item continues to manage each individual variant's barcodes.

At checkout, scanning any main barcode opens a variant chooser with option,
price, and stock details. Selecting a variant adds one of that variant to the
cart; cancelling adds nothing. A group with no active variants cannot be sold.
Scanning an individual variant's barcode still adds that variant directly.

Grouping keeps each product ID, barcode, stock record, and transaction history.
The Size field follows the reviewed Size option. To rename or change that option,
use this dialog rather than editing Size independently.

Select a group or one of its members and use the same action to manage it. You can
attach existing items, generate new variants, or remove rows to detach them. Removing
all members leaves their products intact as standalone items. Edit Item also has a
**Manage variants...** button. Existing pictures and prices remain editable through
Edit Item; grouping does not overwrite them.

## New products

In New Item, select **Product with variants...**. Set the main name, department,
item type, brand, shelf, and default prices. Enter values such as
`Color=Blue,Red; Size; Flavor`, then select **Generate combinations**. Blank
standard separators are omitted from generated combinations and product names.

Remove unwanted combinations and fill each new row's prices, opening stock,
reorder level, and picture. Use **Choose picture** for a local file, or enter an
image URL. Blank barcodes are generated on the server; blank SKUs use the existing
product SKU generation. Duplicate option combinations are rejected. The entire
batch saves together or rolls back.

## Inventory and checkout

Groups start collapsed. Click the arrow or use Left/Right to expand or collapse.
Searching opens matching groups. Parent stock and price summaries cover all active
variants at the current location; the label states when only some options match
the current results. Sorting keeps each group's rows together.

Hover an individual variant to preview its picture. Hover the parent to see up to
nine labeled pictures and an additional-options count. Expand the group to access
every returned option. Enter on a parent expands it; select an individual variant
to sell or edit. Scanning an exact barcode still resolves that individual product.

## Deployment and verification

Install schema/server support before updated registers. The local baseline and
`20260906120000_product_variants.sql` cover fresh installs and existing stores;
the schema contract upgrade runs before normal server readiness validation.
Existing groups also require `20260910120000_product_group_barcodes.sql`, applied
automatically by the schema contract upgrade. Barcode fields live on the existing
`product_groups` row, so LAN reference snapshots and cloud mirror/recovery include
them without a new table or cloud permission. Saves from older registers preserve
the barcode fields when omitted.

`product_groups` precedes `products` in recovery manifests and is included in the
existing global row mirror. The cloud baseline stores catalog rows in its generic
mirror/recovery payloads rather than public product tables, so this feature does
not add a register-facing cloud table or new cloud grants. Group option values are
included in mirrored product rows. Live LAN reads remain authoritative.

Run `mvn -q test`, the repository security script, and `git diff --check`.
`ProductVariantIntegrationTest` additionally accepts
`-Dsmartstock.variants.test.jdbc=jdbc:postgresql://127.0.0.1:PORT/DISPOSABLE_DB`
and `-Dsmartstock.variants.test.user=TEST_USER`. It installs and restructures the
schema: supply only a disposable test database, never an installed store database.

Before deployment, verify the actual Swing dialog, image hovers, keyboard/scanner
checkout, and printed receipts in the installed application. Automated database
and table-rendering tests do not establish physical scanner or printer behavior.
