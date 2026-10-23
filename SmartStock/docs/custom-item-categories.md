# Custom item categories

Open **Company Preferences → Custom Item Categories** to add or rename a category. This is the category list used by Custom Items, alongside the built-in Shirts, Sublimation, Business Cards, Large Format Printing, and Laser groups. It is separate from inventory departments.

In Custom Items, choose the new category in Item Details or the inventory table's Category column, then save. Variants inherit their parent item's category. One item belongs to one service category. Renaming a category preserves its assignments.

The website uses this same list and includes active, unarchived assigned items. Empty groups stay hidden. Changes travel through the existing store catalog synchronization; the browser refreshes its catalog every minute while visible and when focused. No frontend rebuild is needed for later category or item changes. The initial feature update still requires deployment of the server, website bundle, and hosted storefront worker.

The authenticated LAN catalog returns category choices and item assignments to registers. Company Preferences permission is enforced on the server for category creation and renaming; item assignment uses existing custom catalog edit permissions. Registers need no database credentials. Category tables stay in the local store's private storefront schema, are installed by the common StorefrontSchema installer on new and existing servers, and are included in company database backups. Migration 017 adds indexed category assignments without changing the public register-reference schema.
