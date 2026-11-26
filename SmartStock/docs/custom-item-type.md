# Custom item Item Type

Item Type belongs to the main custom item (`custom_order_items.item_type_id`).
Variants inherit it through their parent; no duplicate type or override is stored
on `custom_order_item_variants`.

- Windows Custom Order Items uses the existing classification selector near the
  item name and department for both add and edit. Selector loading follows the
  displayed input, including forms that place its fields separately.
- Custom Inventory shows an editable Item Type on the main/group row and a
  read-only inherited value on expanded variants. Changes stage only the main
  item and immediately update visible children and search matching.
- The shared web Add Item/Edit Item form places Item Type near the item name and
  department. Variant forms show a read-only value from the main item.
- Web Inventory exposes `item_type_name` on each result. For variants the query
  joins the parent custom item's classification. Cards and details display it.

The existing authenticated catalog mutation, permission checks, and item-type
resolution save the main item's classification. No schema migration or new
register credentials are needed. Existing stock/product type and service category
remain separate fields.

Validation: CustomItemInventoryPanelTest; InventoryWebAccessTest;
InventoryWebDatabaseTest against its disposable local PostgreSQL port 55451;
`node --test SmartStock/test/web/custom-item-type.test.cjs SmartStock/test/web/edit-items.test.cjs`;
`node SmartStock/test/web/item-type-browser.cjs`. The browser test runs the actual
local forms with synthetic API responses and never changes store data.

Deploy through the normal next Windows release, updating POS-SERVER and registers
for the desktop changes and refreshing the hosted employee web assets for the web
changes. Source implementation does not change the currently installed release.
