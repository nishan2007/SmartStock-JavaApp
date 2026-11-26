# Phone Minutes

Open **Phone Minutes** from the main menu or a customer's account details. Record the whole-GYD selling amount after sending the top-up. A destination phone number is required for walk-ins and optional for sales linked to a customer account. Select Credit and an active customer, or Cash with an optional customer. Saving does not print a receipt.

Credit minutes increase the customer's total account balance. Unpaid minutes have no limit and do not consume merchandise credit. The account details screen shows minutes due, merchandise available credit, and released account credit separately.

Use the existing customer payment flow to select unpaid minutes, merchandise, invoices, or custom orders. Partial and mixed payments are supported. Statements and payment receipts include the minutes reference and destination phone number. Cash minutes and refunds enter drawer/balance-sheet tender activity; credit collections enter the existing account payment path once.

Search minutes history by customer name, destination phone, or reference; filter by date and Cash/Credit. Load Older retrieves earlier entries. History retains original entries and reversal reasons.

## Customer search and web entry

The desktop split workspace searches active customers by name, account number, or phone. Type in Find customer, then select the matching account; an unrecognized typed name cannot charge a previously selected customer. The selected account summary uses server balances. Search history by customer, destination, or reference, and use the calendar button and Cash/Credit filters to narrow the list.

The mobile Customer First screen is served at /employees/minutes.html through the existing approved employee-browser gateway. Administrators can open **Phone web app** on the desktop minutes screen to issue its existing browser activation flow. Sign in with an authorized staff account and complete any required MFA. Both browser approval and Customer Accounts permission are required. Browser cash sales require an open, active drawer assigned to the signed-in cashier at the configured store; the browser cannot choose another store or cashier's drawer. Reverse Phone Minutes permission and a reason are enforced on the server.

The web interface calls the same local PostgreSQL accounting service as paired LAN registers. It has no direct PostgreSQL or Supabase credentials and no top-up provider integration. Browser retries use a durable request key, scoped to the approved browser and staff member; altered input cannot reuse a completed key. The additive local migration 20261105183000_phone_minutes_web.sql stores these retry responses and participates in provisioning and existing snapshot recovery. HTTPS/origin, HTTP-only sessions, CSRF, location membership, permission and primary-server checks remain in force.

For an isolated visual preview, run `node SmartStock/tools/preview-phone-minutes.mjs --fixture`. It binds to loopback port 4173 and uses synthetic in-memory customers only; it is not a store or an accounting persistence test. The integration test instead creates a disposable PostgreSQL cluster and exercises the real authenticated LAN and employee HTTPS handlers, including committed rows, replay protection, stale remote synchronization, and refunds. Neither test requires a production database.

## Corrections

A user with Customer Accounts and Reverse Phone Minutes permissions can reverse an entry at its originating store, with a reason. ADMIN receives the reversal permission by default; assign it explicitly to other authorized managers. Cash reversals require an active drawer and record a cash refund. For credit reversals, outstanding debt is cancelled and prior payments become available account credit. Choose APPLY_CREDIT when allocating a customer payment to use this credit without collecting cash. Original allocations remain visible, and credit use links retain their source reversal and destination payment. Enter a corrected sale separately.

## Deployment and recovery

The additive local migration is 20261105120000_phone_minutes.sql. Existing provisioning and installer schema-contract paths include it. The cloud migration 20261105120100_phone_minutes_permission.sql extends only permissions and existing service-role snapshot readers. Minutes records are stored in the existing private JSON snapshot/recovery system; no register-facing cloud POS tables are added.

Update participating store servers and apply the cloud migration before depending on cross-store minutes balances. Verified snapshots must include phone_minutes_sales; an older snapshot blocks merchandise credit checks until refreshed rather than treating missing minutes debt as zero. Recording and reversing unlimited minutes remain available when another store is offline or stale. Remote history is read-only; payment allocations and reversals operate on the originating store's records. Released minutes credit is available at its originating store.

No software release, cloud migration, live database, installed application, or hardware deployment is performed by this source change.
