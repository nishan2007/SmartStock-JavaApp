# Sales Tasks

Open **Sales Tasks** from the main menu or **Point of Sale → Sales Tasks**.
This desktop workflow handles copying, printing, laminating, and other service
work that is dropped off before quantities and prices are known.

**Orders → Work Queue** is the combined daily work list. It shows pending custom
orders and Sales Tasks together, sorted by deadline, with customer, assignment,
instructions, overdue indicators, and separate awaiting-pricing/ready-for-pickup
stages. Search or filter either kind of work, then double-click a job or choose
**Open Selected Job** to continue in its existing workflow. Delivered, collected,
and cancelled work is excluded. Refresh the queue or return to the Orders window
to see changes made in another window. Staff see only the work their permissions
allow; an assigned-orders-only employee sees their own custom orders.

## Staff workflow

1. Choose **New Task** in the left task list. In **Customer & deadline**, enter
   a customer name. An account, phone,
   store-local deadline, assigned employee, and initial advance are optional.
   Use **Lookup…** to find an account by name, phone, account number or email;
   selecting it fills the name and phone. **Clear** switches back to a walk-in.
   For a deadline, check **Set deadline**, choose the calendar date (or **Today** /
   **Tomorrow**), and select the hour, minute and AM/PM. No date/time typing is needed.
   Choose **ASAP** instead when work is needed as soon as possible. It displays
   as ASAP on the task and claim slip and sorts first in the combined Work Queue.
   ASAP does not invent a due date or mark the task overdue.
   Use **Next: work & items** to enter instructions, then **Create task** or
   **Save progress**. Add attachments after the task has been saved.
2. In **Work & items**, search the inventory selector, enter a quantity and choose
   **Add to task**. The unit price comes from the inventory item. The inline table
   supports quantity changes and permission-controlled price/discount changes.
   **More → Add service charge** adds a miscellaneous charge. Each row's **…**
   menu provides its discount and removal actions (also available with Shift+F10).
   Use the task search's **…** menu to filter or refresh the left queue.
   Save provisional changes with
   **Save progress**; quantities and prices may remain blank until final pricing.
   These estimates do not create a sale, reserve stock, or establish a debt.
3. Take additional advances using **More → Add advance / payment**. Cash needs an assigned open
   drawer; other tenders need a reference. The claim slip includes a numbered
   payment history and can be reprinted. Account-linked payments also appear in
   customer payment history, where individual receipts can be printed.
4. The primary footer action changes from **Start work** to **Mark work completed**.
   It saves any draft edits before changing the status. Completion does not require pricing.
5. In **Final pricing**, confirm actual quantities/prices and choose **Finalize bill**. Normal sale
   tax, rounding, miscellaneous-item, discount, and price-approval rules apply.
   Finalization requires completed work and at least one positive final charge.
6. In **Pickup & payment**, choose **Collect & take payment** and settle the displayed balance through a supported tender or an
   eligible customer account. Cash received can exceed the balance; SmartStock
   displays change. Excess advances must first be refunded.
7. Use **Final receipt** after collection. The linked sale includes the net tender
   breakdown, including advances. Stock is deducted only when collection commits.

Use **Reopen Work** or **Reopen Pricing** with a reason to revise uncollected work.
These controls, cancellation and the detailed history are under **More**.
Cancellation also requires a reason and refunding all advances first. Collected
tasks use the existing sale return/correction workflow.

## Permissions and persistence

- `MANAGE_SALES_TASKS` controls the queue and work changes. Admin receives it by
  default; grant it to the appropriate staff roles.
- Payments, final pricing, and collection also use `MAKE_SALE`. Refunds require
  `PROCESS_RETURNS`. Existing miscellaneous-item, price, discount, credit-limit,
  expired-account, signature, and multi-store balance rules remain enforced by
  the server. Assignment is restricted to employees associated with the store.
- `/v1/sales-tasks/read` and `/v1/sales-tasks/update` require an authenticated
  device and employee session. Updates require an idempotency key and the last
  task version. The server serializes collection and rejects stale edits.
- Charge lines are persisted as checkout JSON on the task. Audit events retain
  every finalized bill and subsequent revision. Payments are an append-only
  task ledger. Collection creates one linked ordinary sale in the same transaction
  as settlement, inventory changes, and closing the task.
- Attachments are stored as PostgreSQL binary values in the active local store
  database, rather than application or rollback folders. Limits are 5 MB per
  file, 20 files and 50 MB per task. Downloading saves a file without executing it.
  The recovery/mirror manifests include tasks, payments, events, and attachment
  bytes. Shared cloud SQL adds only the permission; the POS authority stays local.
- Drawer and balance-sheet receipt queries use `sales_tender_activity`, which
  combines ordinary sale receipts with signed task payments and excludes the
  linked task sale. Task refunds reduce net tender income. Revenue/item reports
  continue to use the final sale, so advances do not create duplicate revenue.
- Schema provisioning uses the local baseline and ordered
  `20261025120000_sales_tasks.sql` migration; existing-server readiness installs
  it transactionally. The companion permissions migration covers the cloud
  catalogue. No live store installation or publication is part of this change.

## Validation

The normal Maven suite includes the non-database rule tests. Integration tests
are opt-in against a **disposable database only**:

```powershell
mvn -q -f SmartStock/pom.xml '-Dtest=SalesTaskDatabaseTest' '-Dsmartstock.test.salesTaskDatabaseUrl=jdbc:postgresql://127.0.0.1:55473/sales_tasks_test' test
```

This suite resets the `public` and `storefront` schemas in that named test database.
It does not read configured store credentials. It covers lifecycle, mixed charges,
tax freezing, advances/refunds, accounts, receipts, cash reconciliation, retries,
concurrent pickup, stock timing, failed collection, scope/permission checks, and
attachments. Live installed-app upgrades and physical printer/drawer behavior
require separate verification.
