# Internal custom order spoils

Start the Mobile Item Web App on the store server through Status. In Custom Orders or Orders, use **Custom Order Spoils / Phone QR**, scan the activation QR on a phone connected to the store LAN, and sign in with employee credentials. The existing local HTTPS certificate trust setup applies.

In Role Management, grant **Record Custom Order Spoils** to employees who may report spoils, and **Reverse Custom Order Spoils** to managers who may correct reports. Administrators receive both permissions. Recording does not require catalog-edit permissions.

Find an order by number or customer, choose its exact line, add one to three spoil photos, and enter a reason. Review the replacement stock effect and submit. Photos are optimized to JPEG (maximum 2 MB each) and remain internal evidence linked to the report, order, and line. They are stored with the database records for atomic save and inclusion in database backup and store-scoped off-site recovery.

Each report consumes one additional unit for an inventory item or its exact variant. Repeated spoils are separate reports. Service and customer-supplied lines do not consume stock. Sales counts, customer prices, payments, and production status remain unchanged. Negative stock is allowed and flagged. Delivered/cancelled orders, delivered lines, and fully returned lines reject new reports.

If the phone loses the response, use **Retry / confirm previous spoil**. The report ID and photos are kept in the phone tab session, and the server prevents repeated stock deduction. A definitive validation rejection releases the form for correction. Do not clear the browser session while an uncertain submission is pending; check internal history first.

On the desktop Orders screen select a saved order and open **Internal Spoil History** to view evidence. Managers can reverse a report on the desktop or phone with a reason. Reversal restores only stock originally deducted, can occur after delivery, and preserves all evidence and audit history.

The local schema contract includes the ordered spoil-table migration. The cloud schema contract adds spoil permissions; spoil records and photos use the private store recovery mirror, without cloud POS tables. Fresh local installer seeds include permissions; existing stores receive the additive schema upgrade. Spoil tables are included in store-scoped cloud mirroring and recovery. Customer-facing attachment, proof, and storefront routes never use spoil tables.

Automated validation uses a disposable PostgreSQL cluster and a mocked phone browser API. Live phone camera, HTTPS trust/activation, installed Windows app, deployed server permissions, and real-store stock/backup restoration require separate verification before operational rollout.
