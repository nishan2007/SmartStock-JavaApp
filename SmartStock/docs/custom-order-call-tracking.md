# Custom order call tracking

In Custom Orders lookup, select an order and open **Delivery / Calls**. Select
one or more item lines (or **Select All**), choose **Called** or **Called - No
Answer**, add optional notes, and save. Use **Delivered** when handing over the
items later.

Each selected line shows its latest call outcome in its delivery status. Every
attempt is recorded separately in Activity with the staff member, timestamp,
line ID, and notes, even when the outcome is unchanged. Calls do not change
production, payment, stock, or delivery timestamps. Delivered, fully returned,
and cancelled orders/lines cannot be updated through this action.

The server requires the existing custom-order line delivery or override
permission. Call recording does not require the unpriced-delivery approval.

The base local schema and ordered migration
`20261019160000_custom_order_call_status.sql` extend the existing line delivery
status constraint. SchemaContractService registers and applies the upgrade for
existing stores; installers use the same schema resources. Existing line and
audit synchronization carries these values without new fields or indexes.

Live PostgreSQL migration and installed Windows UI verification must be done
before deployment. This change does not install or update a live store.
