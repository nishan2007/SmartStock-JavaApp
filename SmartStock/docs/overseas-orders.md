# Overseas orders

Dedicated phone entry point: https://overseasshipping.deckers.gy/ . The Cloudflare
Worker in `cloudflare/smartstock-overseas` serves the overseas workspace and
forwards only activation, authentication, bootstrap and overseas-order API paths
to the existing employee gateway. It validates the exact public Origin before
translating it to the gateway's canonical origin; backend CSRF and permissions
remain enforced. Payroll endpoints and the register API are not forwarded.

Install SmartStock 1.1.63 or later on the store server before using this site.
Until then, the site displays a server-update notice. Generate a browser QR from
**Status > Employee Web App**. When its time-clock invitation opens, choose
**Activate this browser for Overseas Orders**, then activate and sign in. Each
domain has its own browser cookies; an existing time-clock browser activation
does not automatically activate the overseas domain. The invitation retains its
one-use, ten-minute lifetime. Do not activate it on the first domain if it is
intended for the overseas site.

Open **Orders > Overseas Orders** on a store register. Activated employee browsers
also show Overseas Orders after sign-in when the user has an overseas permission.
Choose an assigned store when using the phone app. The gateway continues to use
the local store database; cloud services are not the register-facing authority.

## Quoting

Find an existing customer or enter a name and WhatsApp phone number for a new
account. Duplicate phone ownership is rejected on issue, so select an existing
account rather than creating another account with the same number.
Typing a complete phone number automatically checks the existing customer
database, including local and international formatting. Matching accounts appear
for explicit selection; inactive or reserved numbers show an explanation.
The same automatic lookup appears beneath the phone field in Customer Orders
and the desktop Overseas Orders screen. Duplicate phone ownership is also
checked when saving an overseas draft, with the database guard still arbitrating
concurrent account creation.

Add descriptions, links, sizes/colours/options, quantities and GYD unit selling
prices. Optional USD costs and exchange rates are private reference fields; they
never calculate the customer's selling price or appear on printed documents.
GYD unit selling prices already include shipping. A separate **Extra shipping**
charge is optional: leave it blank or zero unless an additional charge is needed.
Missing, blank and null extra shipping amounts are saved as zero. The total is
the item prices multiplied by their quantities, plus any explicit extra charge.
Phone entry follows **Customer → Items → Delivery → Review cart**. Add or edit
one item at a time, then review item quantities, screenshots, subtotals, any extra
shipping charge and the final total in the cart. Zero extra shipping is omitted
from printed item lines; slips state that item prices include shipping.
Back and Edit buttons retain entries. Save the draft or issue the quote from
the final review; issued orders open in a compact tracking overview.

On the phone, select or paste PNG/JPEG screenshots under 2 MB while adding items.
They stay in the browser until saving; saving uploads and attaches them before
issuing. Failed uploads retain the selected screenshot for a save retry. On
desktop, save a draft first, then edit an item to attach a screenshot and save
the quote again. Screenshots
use the existing private image registry, local mirror and off-site image backup.
Issuing creates a permanent `OS-store-number` quote and requires no payment.

## Payments and collection

Each order belongs to its selected store. On the phone, choose the assigned store
before starting a quote; the customer step and final cart confirm it. Desktop
orders use the register's active store. Tracking and slips show the configured
store name (for example Skeldon or Rosehall). Existing orders cannot silently move
between stores; store ownership is enforced by the server.

Items show **Waiting for payment → Processing → Ordered → Arrived at Deckers
[store]**, followed by Collected. A recorded payment automatically changes
unplaced items to Processing. Staff mark selected items Ordered after placing the
online purchase, then Arrived when received at the assigned store. These actions
update the existing quantity records and audit history. The quantity editor
continues to support partial orders, arrivals and collections. Shipping transit
quantities remain available there without adding another required tracking stage.

Each item also has a free-text Seller field. Saved seller names are available as
suggestions in future quotes for that store, with case-insensitive deduplication.
Type a new seller (Amazon, Temu, or another name) or select a saved suggestion.
Seller names appear on cart items and printed slips. The seller column and its
index are covered by migration `20261021130000_overseas_sellers.sql`, both schema
baselines and local/cloud upgrade contracts. Existing recovery manifests already
include the item rows, so sellers are mirrored with those rows.

Accept any positive instalment or full payment at the register. Cash requires an
assigned active drawer. Card, cheque and MMG require a reference. Account charging
is not a payment method for these orders. The first payment prints an order slip
and a transaction receipt through the configured order-slip printer. Print slip /
receipt also reprints individual transactions. Receipt snapshots retain the totals
and balance at payment time; printer failure never repeats the payment.
Overseas balances remain visible in account history, but Customer Accounts does
not allocate payments to them; collect those payments from Overseas Orders.

Shipment and collection dialogs use **cumulative totals**, not quantities to add.
Every item must satisfy:

`0 <= collected <= arrived <= shipped <= purchased <= ordered`

Partial collection may leave any balance, even if that balance exceeds the value
of the uncollected goods. Collecting the final outstanding quantity requires the
entire balance to be zero. A payment and collection can be posted atomically; if
collection is rejected, the accompanying payment rolls back too.

Quotes with payments or purchasing progress require adjustment permission and a
reason before editing. Totals cannot fall below payments received. Quantity
reductions require adjustment permission and a reason. Completed collections can
be corrected by an authorized employee. Refunds preserve payment history and
reopen the balance; high-value refunds also require the existing custom-order
refund approval permission. Refund payments before cancelling. Cancellation needs
a reason, clears the outstanding payable amount explicitly, and is blocked while
items remain recorded as collected.

## Permissions and interfaces

Assign the **Orders / Overseas Orders** permissions in role management:
`OVERSEAS_ORDERS_VIEW`, `OVERSEAS_ORDERS_QUOTE`, `OVERSEAS_ORDERS_PAYMENT`,
`OVERSEAS_ORDERS_SHIP`, `OVERSEAS_ORDERS_COLLECT`, `OVERSEAS_ORDERS_ADJUST`.
Admin receives these by default. Phone use additionally requires
`ACCESS_EMPLOYEE_WEB` and browser activation. Permissions and store assignment
are checked on the server. Phone and Remote Admin sessions cannot record payments,
refunds, cancellations or collections. Registers must have Allow Orders enabled.

The authenticated LAN endpoint is `POST /v1/overseas-orders/workflow`; the employee
gateway exposes `POST /employees/api/v1/overseas`. Both dispatch `action` values
`LIST`, `DETAIL`, `CUSTOMERS`, `IMAGE`, `SAVE`, `ISSUE`, `UPLOAD`, `PAYMENT`,
`SHIPMENT`, `COLLECT`, `REFUND`, `CANCEL`. Mutations include a UUID `requestId`;
existing orders include `id` and the loaded `revision`. Duplicate requests return
their stored result; changed payloads cannot reuse a request ID. Stale revisions
are rejected. The caller owns one transaction for identity, mutation, audit and
request completion.

Migration `20261021120000_overseas_orders.sql` is included in local/cloud schema
contracts and baseline provisioning. Recovery and mirror manifests include all
overseas records. Off-site tables are service-role-only. Screenshots remain private
and are retained by image-reference reconciliation. No production migration,
installation, cloud publication or hardware validation is performed by tests.

## Validation

Normal checks: `mvn -q -f SmartStock/pom.xml test`, Git Bash
`SmartStock/tools/security-check.sh`, and `git diff --check`.

Database tests are opt-in and accept **only**
`-Dsmartstock.overseas.test.jdbc=jdbc:postgresql://127.0.0.1:55443/postgres`, using
an isolated disposable PostgreSQL cluster owned by `overseas_test`, with trust
authentication restricted to loopback. They install the actual local baseline,
then test quoting, payment replay/concurrency, collection, refunds, receipt
snapshots, access boundaries and transactional rollback. Never point them at a
store database. Stop the disposable cluster after testing.

`node SmartStock/tools/test-overseas-web.cjs` uses Playwright with mocked API
responses to exercise the phone and desktop browser editor without store access.
Playwright must be available through normal Node resolution or `NODE_PATH`.

Before live use, verify authenticated remote-phone access, installed desktop
navigation, screenshot backup/recovery, each physical printer destination and
cash-drawer operation. No WhatsApp integration, scraping, purchasing automation
or customer self-service is included.
# Separate overseas cash

Enable **Handle separate overseas-order cash (no float)** in Cash Drawer Manager
for each drawer that will accept overseas cash. Close any open session before
changing the option. The setting is captured when the next session opens.
Overseas cash payments require an enabled drawer; card, cheque and MMG retain
their existing payment handling. The payment amount is the exact cash collected;
there is no overseas float, tendered amount or change calculation.

In **Balance Draw**, exclude overseas cash from the normal denomination count.
Use the separate section at the bottom to enter the overseas cash actually held.
A shortage or overage requires a reason. Closing records expected cash, counted
cash, difference, reason, cashier and time, and transfers the counted amount to
the store's overseas fund. The next session starts this section at zero. Close
receipt reprints retain those figures. Normal drawer corrections do not change
the overseas handover.

**Balance Sheet** has a separate overseas fund section with its own B/F, received
cash, expense/withdrawal column and C/F. It covers the selected dates for the
whole store, even when normal cash is filtered to selected drawer sessions.
Only closed-drawer handovers enter the store fund. Use **Overseas cash withdrawal**
for money removed for supplier payments or other purposes; enter the amount and
payee/purpose. Withdrawals require Balance Sheet permission, are audited and
cannot exceed the store's available fund. Do not also enter them as normal
expenses. Saved submissions and exports retain the separate fund snapshot.

Use **Overseas Cash Checker** beside the overseas fund activity to enter the
quantity of each GYD note held in the store fund. It shows expected C/F, counted
cash and the missing/over amount independently of the normal CF Checker. Include
only cash already handed over to the fund; open drawers are counted when closed.
Enter a reason for a shortage or overage, then submit the Balance Sheet to save
the denomination breakdown, server-calculated count/variance, reason, user and
time. Saved submissions display the original count read-only and include it in
print/PDF/PNG exports. Earlier submissions without a count say so explicitly.
If the fund balance changes before submission, refresh and verify the count
again. Verification records a discrepancy; fund receipts, withdrawals and normal
CF remain ledger-derived.

Cash refunds reduce the current session's overseas cash and require enough cash
collected in that section; they never spend the normal float. Closed handovers
and withdrawals remain in history. Earlier payments keep their original cash
classification; enabling this feature does not move historical cash.

Before live use, verify physical segregation, handover, receipt printing and
cash-drawer operation with store staff. No live drawer or database is changed by
the automated tests.

