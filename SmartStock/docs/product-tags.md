# Product tags

Inventory products support multiple reusable tags alongside their department,
item type, brand, and shelf. Names are case insensitive and whitespace is
normalized. New tags are Internal.

Open **Inventory → Tags…** to manage the shared tag list. Check products or select
a product row to add or remove a tag in bulk. Add and Remove preserve all other
tags. The dialog shows how many selected products already have the chosen tag.
Edit Item also has a Tags action; New Item can open tagging after saving.

Users with `EDIT_ITEM` can create, assign, rename, change visibility, and merge
tags. The authenticated server enforces this permission. A merge moves all
assignments into the destination tag and keeps the destination's visibility.

Inventory and checkout searches include Public and Internal tag names. Exact
barcode/SKU identification retains its existing behavior. Inventory's tag filter
matches any selected tag and combines with the existing filters.

Only Public tags are included in the server's storefront product snapshot.
The website searches these tags and shows them as selectable filters. Internal
names and assignments are not sent to the customer browser. A previously
published snapshot may remain visible while a store is offline; normal
storefront synchronization refreshes visibility changes.

Local PostgreSQL owns definitions and assignments. LAN clients use the existing
authenticated API and invalidate their catalog cache after changes. Recovery
snapshots include both tables; product notifications transfer assignments with
the catalog while preserving compatibility with snapshots from older servers.

The idempotent migration is `20261123120000_product_tags.sql`. Fresh local and
cloud schemas include the same tables, RLS restrictions, unique name index, and
reverse assignment index. Existing local upgrades use the schema contract's
guarded transaction. No live database or website deployment is part of editing
these sources.

Database regression tests require a disposable PostgreSQL instance on loopback
port 55443 with user `tags_test`; run Maven with
`-Dtags.test.admin=jdbc:postgresql://127.0.0.1:55443/postgres`.
