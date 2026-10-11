# Custom orders with price pending

Select **Price pending** before adding items to a new custom order. Staff can
save customer details, due dates, instructions, attachments and quantities while
leaving prices and measurements unfinished. A reason and either
`CUSTOM_ORDER_DEFER_PRICING` permission or manager approval are required.

Deposits are optional. Existing payment methods, references, drawer sessions and
payment permissions still apply. Pending orders show deposits separately from
the unknown total and balance; they are never considered paid in full.
Percentage minimum deposits are calculated once the price is final.

Production can proceed normally. Delivery before pricing requires a separate
manager approval for `CUSTOM_ORDER_UNPRICED_DELIVERY_OVERRIDE`, with a reason.
An approved delivery does not prevent later pricing.

In order lookup, users with `MANAGE_CUSTOM_ORDERS` can select **Finalize price**.
Enter the final measurements, rates and add-on charges for every saved unit.
Existing customer discounts, rounding, manual discounts, price override approvals
and customer charge authorization apply. The server calculates the final total
and subtracts recorded deposits. If deposits exceed that total, use **Refund**
to refund the excess with the existing refund permissions and approval threshold,
then finalize again. Before cancelling an unpriced order, refund its deposits.
Ordinary line returns require a final price; cancellation still records each
line's stock disposition.

Finalization updates existing lines and keeps their production, delivery and
attachment history. It does not deduct inventory again. Payments, refunds and
finalization use transactional order locks and the LAN API's existing idempotency
keys. A retry of the same request returns its saved result.

## Schema and compatibility

The local base schema, seed, ordered pricing migration and startup upgrade carry
the pricing columns, constraints, index and permissions. Existing orders default
to `FINAL`; omitted pricing state retains the original creation behavior.
Local/server provisioning and platform installers use the same schema contract.
The separate permissions migration also updates the cloud role catalogue;
register-facing orders remain authoritative on local PostgreSQL through HTTPS.
Reference and recovery mirrors retain the new fields through their generic row
payloads. Approval tokens are consumed server-side; stored evidence contains
actors, approvers and reasons, not the tokens.

## Verification

The optional database integration test requires a disposable PostgreSQL cluster
listening only on `127.0.0.1:55443`, with user `pricing_test`. Run:

```powershell
mvn -q -f SmartStock/pom.xml '-Dtest=CustomOrderPendingPricingDatabaseTest' '-Dpricing.test.admin=jdbc:postgresql://127.0.0.1:55443/postgres' test
```

The test creates and drops its own database and refuses any other database URL.
It exercises migration compatibility, permission and approval-token enforcement,
production, delivery, deposits, refunds, finalization, idempotency and concurrent
payments. Without the explicit URL, this integration test is skipped.

Interactive Swing entry, pricing and approval dialogs, installed-store schema
upgrades, physical slips and cash drawers must also be verified before deployment.
