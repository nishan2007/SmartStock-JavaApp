# Order item exchanges

Administrators at an enrolled store register can use **Orders → select order →
More → Exchange item**. The action is also in the detailed order's Items tab.
Both preview and save enforce the active ADMIN role on the store server; granting
general order permissions to another role does not enable exchanges.

Select the original line, search for the replacement by name, SKU or barcode,
and select a variant. Explicitly choose whether the original item goes back into
inventory, enter a reason, and preview the exchange before confirming.

- The server uses the replacement's current catalog price, retains print add-ons
  and the existing line discount percentage, and shows the revised order total.
- A more expensive replacement creates a balance due. Required customer charge
  authorization is captured again when the outstanding amount increases.
- A cheaper replacement reduces the balance first. If payments exceed the new
  total, the excess must be refunded as part of the exchange. Select the refund
  method and provide the provider reference for non-cash refunds. Process the
  external refund before confirming. Cash refunds require an open assigned drawer
  and are recorded in that drawer, without changing earlier closed drawers.
- Returning the original item restores one unit to stock and releases its
  reservation. Choosing No consumes the old reservation and leaves its stock out.
  One replacement unit is issued and reserved. Parent totals, stock movements,
  financial history, production history, and the order audit update together.
- Existing personalization, files, and print charges stay on the line. Item
  identity, size, color, and print-template dimensions switch to the replacement.
  Its production stage resets to Not started; the order production stage is
  recalculated from its lines.
- Concurrent stock, price, payment or order changes invalidate the preview.
  Request keys prevent a network retry from issuing stock or refunding twice.
  An uncertain network result offers Retry using the same frozen request.

Initially this supports fixed-price inventory items in the custom-order catalog,
including variants. Delivered/cancelled orders, delivered or returned lines,
pending pricing, customer-supplied/area-priced/regular-product lines, invoice or
account billing, allocated account payments, loyalty redemptions and manual
balance adjustments must use their respective existing workflows first. A swap
never rewrites an old payment or pretends that additional money was collected.

No schema migration is needed: exchanges use existing order, reservation,
movement, payment, authorization, audit and outbox tables.

## Validation

`CustomOrderExchangeServiceTest` checks price differences and currency rounding.
`CustomOrderExchangeDatabaseTest` installs the full local schema in a temporary
database on an explicitly selected disposable cluster:

```powershell
mvn -q -f SmartStock/pom.xml '-Dtest=CustomOrderExchangeServiceTest,CustomOrderExchangeDatabaseTest' '-Dexchange.test.admin=jdbc:postgresql://127.0.0.1:55479/postgres' test
```

The cluster must use the test-only `exchange_test` role. The test never reads
store credentials, refuses other JDBC targets, and drops its generated database.
Run normal Maven and security gates as well. Installed server/register behavior,
physical stock return/issue, external provider refunds and physical cash handling
require separate acceptance after an authorized deployment.
