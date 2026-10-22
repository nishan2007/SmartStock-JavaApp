# Customer loyalty

Loyalty ships disabled. In Company Preferences > Loyalty, an administrator selects
the authority store and enables the programme. Defaults are 1 point per G$100,
G$1 per point, a 100-point minimum redemption and a 20% eligible-bill cap. Points
do not expire. Settings changes preserve the monetary value of existing awards.

Use **Product eligibility / exclusions** to exclude delivery, gift-card purchases
and any other non-qualifying products or custom items before enabling loyalty.
Tax is excluded automatically. Eligibility is captured on document lines; changing
a product later does not change previously earned rewards. Catalogue eligibility
uses existing catalogue synchronization, while the points ledger stays local.

## Checkout and custom orders

Select a customer at the register, then choose **Loyalty points**. Checkout asks
the server for an exact discount and total. Editing the cart, customer or discount
clears that preview. The server validates the balance, current redemption limits
and prices again when completing the sale. Existing customer discounts stack;
points accrue on the remaining eligible spending.

For a custom order, save its final price without a payment first. In its details,
choose **Redeem loyalty points before first payment**, then take the deposit or
payment through the normal workflow. Redemption is locked once a payment history
exists. Orders billed through an invoice use their existing invoice workflow and
cannot redeem through the production order. Custom orders earn only when fully
paid and completed, ready for collection, or delivered. Invoice-billed production
orders follow the linked invoice payment status; their zero production balance
does not release points. Register credit sales earn only when fully paid.
Payments and deposits do not earn separately.

Receipts show points redeemed, the discount, earnings pending/earned and an
available balance. If the authority is unreachable, printing continues and the
balance is explicitly unavailable. Customer Accounts > Loyalty shows history;
administrators at the authority can post reasoned adjustments.

Returns refund the money actually charged and restore the redeemed points.
Exact line totals preserve cents across quantity-based partial returns. A partial
return restores a proportional whole-point share from each originally spent lot,
preserving its original point value; fractional points wait for further returns.
Reversing an already-spent award can create a negative balance. New earnings
offset that balance in whole points through explicit ledger entries. Awards used
to settle a points debt are consumed at their recorded value; unspent points keep
their original value. Redemption must fit both the available points and value.

## Store-server trust and connectivity

Every register still uses its own store's authenticated LAN API. The designated
authority is a store PostgreSQL server, not Supabase. Supabase carries existing
document mirror metadata for cross-store returns; it never authorizes points.

Configure the following environment variables in the **store service process**,
not in register configuration. Use separate, randomly generated secrets of at
least 32 characters for each origin-authority relationship. Never commit values.

At an originating store, replace `<authorityId>` with the numeric authority ID:

- `SMARTSTOCK_LOYALTY_STORE_<authorityId>_URL`: HTTPS origin for the authority.
- `SMARTSTOCK_LOYALTY_STORE_<authorityId>_KEY`: this origin's shared peer secret.
- `SMARTSTOCK_LOYALTY_STORE_<authorityId>_SHA256`: optional pinned leaf-certificate
  SHA-256 (64 hex characters). Without a pin, standard certificate trust applies.
  Certificates must be valid for the configured hostname and within their dates.

At the authority, configure `SMARTSTOCK_LOYALTY_PEER_<originId>_KEY` with the same
secret for that originating store. No shared keys are exposed by Company
Preferences. Peer requests bind origin, timestamp and body with HMAC; redirects
are refused. Keep server clocks synchronized. Joining stores enter the authority
ID in Company Preferences and save to verify connectivity and adopt its rules.
Change shared policy at the authority. Authority replacement is blocked once
history or unresolved operations exist; migration/failover requires a separately
planned ledger transfer, rather than creating another spendable copy.

The authority can operate without internet for its own registers. Other stores
need connectivity to it to redeem. Purchases continue during outages; durable
document revisions queue earnings, payments, returns and cancellations. New
purchases use their last synchronized earning policy. Existing purchases keep
their captured version. Local backups must include the loyalty tables with the
rest of the store database; the generic cloud mirror is not a ledger backup.

Remote reservations do not expire automatically. Before requesting one, the
origin durably records an intent while holding a document advisory lock. After
the local transaction ends, reconciliation either delivers the committed document
or releases the failed checkout's reservation. If connectivity is uncertain,
points remain reserved until the origin confirms the result. Never clear these
tables or force-release a reservation while a transaction outcome is uncertain.

## Validation and rollout

Local schema installation and upgrades use
`20261021190000_customer_loyalty.sql`. The ordered cloud migration
`20261021190100_loyalty_refund_metadata_cloud.sql` carries exact cross-store refund
totals through the existing refund queue. Apply the cloud migration through the
normal reviewed migration process before accepting cross-store loyalty returns.
No live migrations, installations or releases are performed by implementing this
feature. Older registers can still make ordinary purchases, which earn through
the server, but cannot request redemption.

Run `mvn -q -f SmartStock/pom.xml test`, the Bash security check and
`git diff --check`. The disposable database suite is explicitly gated:

```powershell
mvn -q -f SmartStock/pom.xml '-Dtest=LoyaltyPolicyTest,LoyaltyDatabaseTest' '-Dsmartstock.test.loyaltyDatabaseUrl=jdbc:postgresql://127.0.0.1:PORT/loyalty_test' test
```

That suite resets only the explicitly named `loyalty_test` database on loopback.
Use a temporary PostgreSQL instance. Before enabling in a store, separately verify
peer TLS/HMAC configuration, service restarts, disconnection/reconnection,
simultaneous redemption at physical stores, actual checkout/custom-order screens,
and receipts on the installed printers. Automated accounting tests and rendered
previews do not prove those deployment or hardware checks.
