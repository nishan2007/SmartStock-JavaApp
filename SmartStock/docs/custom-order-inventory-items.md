# Regular inventory items in custom orders

Use **Add from Inventory...** on a new custom order to search regular stock by
name, SKU, or barcode. Select the item, set the quantity, and add the normal print
or engraving add-ons before adding the line. This lookup is separate from the
custom-item search.

The base charge is the product's normal selling price. Existing custom-order
discounts, price-override approvals, deposits, and total-rounding settings still
apply. The server checks the product and selling price when saving. If the price
changed, select the product again or use the existing approved override workflow.

Stock is deducted at the order's location in the same transaction as saving the
order. Each physical item becomes one order line. Insufficient stock rejects the
save; holding a cart does not deduct stock. Held lines retain their inventory link.

Use the existing return or cancellation stock choice: **RESTOCK** returns the
item to regular inventory; other choices leave it deducted. A physical item can
be restocked only once, including across partial refunds. An engraved item that
cannot be sold again should use the appropriate existing non-restock choice.

The schema upgrade is
`20261022180000_custom_order_inventory_products.sql`. It adds the inventory
product link and restock flag to saved order lines. Local provisioning, existing
store upgrades, and the cloud recovery schema include it. No live-store
installation or database migration is performed while preparing this change.
