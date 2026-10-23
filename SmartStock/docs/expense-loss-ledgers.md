# Expense and loss ledgers

Reports now contains **Expense Ledger** (replacing Expenses) and **Loss Ledger**.
Both require the existing View Reports and Balance Sheet permissions. The
expense ledger retains the existing monetary expense report and adds a separate
noncash stock table, with monetary, stock, and combined totals. Existing expense
records are read once; historical receiving shortages are imported by the server upgrade.

## Recording stock use

Grant **Record stock expenses** to employees who may record company use or
donations. Admin receives this permission by default. Select **Record Stock
Expense**, search for an item, enter quantity, category, recipient/description,
and reason, then sign. The employee is the authenticated user and cannot be
changed in the form. A signature is mandatory; there is no unsigned override.
Products, custom stock items, and custom stock variants use their existing
inventory paths. Insufficient stock is rejected on the server.

Stock deductions, ledger entries, inventory movements, and audit outbox records
commit together. There is no sale, tender, cash receipt, or drawer movement.
Ledger value is quantity times the selling price captured when posted. Recorded
cost is also captured for regular products and is visible only with View Cost
Price. Custom items currently have no unit-cost field, so cost is unavailable.
Stock expenses and losses are noncash and do not enter the balance sheet's
monetary expense total or cash carried-forward calculation.

## Receiving losses

For each downward correction, receiving asks only for a reason category; notes are not required.
The server writes the shortage before adding the delivery quantity, linked by
the receiving reference. Upward corrections create no loss. Existing receiving
and stock-override permissions remain in force. New receiving requests include
the originally displayed system quantity: if it changed, refresh and recount.
Legacy clients can still receive unchanged stock; shortages require the new
per-line reason fields. Existing support for negative counted inventory remains.

Use the report date range plus the ledger's product, employee, and reason
filters. Details show quantity and valuation snapshots, notes, and the original
signature for stock expenses. **Receiving Record** opens the associated receiving
history when the employee has View Receiving History permission. Loss totals
are separate from expense totals.

## Corrections and deployment

Grant **Reverse stock ledger entries** separately. Posted records cannot be
edited through the API. A reversal requires a reason, references the original,
restores its stock quantity, and negates its original selling-value snapshot.
The original signature and record remain. An entry can be reversed once;
reversals cannot themselves be reversed. Record a new corrected entry if needed.

Mutations use the LAN API's existing idempotency mechanism. Retry the same request
with the same key after an uncertain network result. Inventory rows are locked
while posting and reversing. Reversal foreign keys are deferred until commit so
recovery can restore a ledger table whose rows arrive out of order.

The local baseline and `20261021180000_stock_ledgers.sql` install the table,
indexes, and permissions. Schema readiness applies the upgrade on the server.
The store-scoped recovery mirror includes the ledger and signature bytes; it is
not added to cross-store catalogue synchronization. Registers use authenticated
HTTPS endpoints under `/v1/stock-ledger/` (`load`, `detail`, `post`, `reverse`).
No register database credentials or live-store installation are required by this
source change.

The database tests create their own loopback PostgreSQL cluster using
`SMARTSTOCK_TEST_POSTGRES_BIN`, defaulting to PostgreSQL 17 on Windows. They never
connect to an installed store. Installed-app, signature-device, and live-server
checks must be performed separately before deployment.

## Historical loss import

`20261022120000_historical_stock_losses.sql` imports negative receiving stock
corrections from regular and custom inventory movements. It preserves their
original dates, quantity, employee (where known), notes, and receiving reference.
The source movement type and ID prevent duplicate imports; modern losses already
linked to the receiving record are excluded. Inventory and cash are untouched.

Historical selling prices and costs were not stored on these movements. Imported
rows are explicitly labeled `CURRENT_PRICE_ESTIMATE`, using prices at import time.
If the product/price is unavailable, valuation is marked `UNAVAILABLE` and omitted
from monetary totals. Counts parsed from the original audit note remain blank
when absent; missing employee/device identities are not fabricated. Fractional
custom adjustments and rows without a valid store are not imported automatically.

## Monetary expense categories

Balance Sheet's Log Expense form and expense-row editor offer a category selector
with Salary, Marketing, Electrical, Construction, Maintenance, Inventory / Supplies,
Rent, Utilities, Internet / Telephone, Transport / Delivery, Fuel, Cleaning,
Security, Professional Fees, Bank Charges, Licences / Permits, Taxes / Government
Fees, Insurance, Equipment / Furniture, Donations, Staff Welfare, and Other.
The selector is editable so an additional category can be entered when necessary.
New expenses require a category; amounts and payment behavior are unchanged.

Payroll-generated expenses automatically use Salary, including existing payroll
expenses refreshed by the balance-sheet service. Reports recognize old Payroll
labels as Salary too. Existing submitted balance-sheet snapshots retain their
original labels and audit history. Other historical custom category names remain
supported; descriptions are not used to guess or override the selected category.

## Moving old company-account purchases into expenses

For an existing company account such as Deckers, open Customer Accounts, open
that account's details, and choose **Transfer Company Expenses**. The action
requires Customer Accounts, Record Stock Expenses, Process Returns, and Return
Override permissions on the server. Review the receipt list, choose Company Use
or Donation, and sign the bulk transfer. The preview identifies the account by
name and number, lists skipped receipts, and is rejected if the account changes
before posting. Transfers are scoped to the register's selected store.

Eligible purchases are complete unpaid account sales containing supported stock
items, with matching account credit and receipt totals and no earlier returns.
Paid/partly paid receipts, receipts with loyalty activity, mismatched discounts,
non-stock/miscellaneous lines, invoices and custom-order charges are not converted
by this action. Skipped charges remain on the account for separate review.

The transaction records a linked ACCOUNT return/correction to offset the original
sale and customer debt, then signed noncash expense rows linked to the original
sale items. It issues no cash and performs no inventory deduction or restock.
Expense rows preserve the purchase date and original receipt unit prices; details
identify the correction and actual transfer signing time. The original sale,
audit history and account transactions remain. Existing purchase stock quantities
are unchanged. Repeated attempts cannot convert the same receipt twice.

Account transfers cannot be reversed using the stock-ledger reversal button,
because that would incorrectly restock inventory without undoing the account
correction. Any correction must be reviewed as an accounting transaction.

For new company use and donations, use Reports → Expense Ledger → Record Stock
Expense directly instead of charging the company customer account. These source
changes must be deployed to the server and registers before the new transfer
button can be used; no live account was converted while developing this feature.
