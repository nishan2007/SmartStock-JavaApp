# Custom-order held carts

New Order has **Hold Cart** and **Resume Held Cart** buttons. Add or finish editing the current item before holding. Name the cart so another cashier can identify it. A successful hold clears the composer; Resume shows all available holds for the authenticated store, newest first.

Holds preserve customer/account details, individual item quantities and grouping, design dimensions/orientation, add-ons, discounts, notes, due date, payment-method choice, deposit entry and attached files. Deposit entries are draft values: holding does not record a payment, place an order or reserve stock. Checkout still validates current inventory, pricing and permissions. Temporary manager approval tokens are removed from stored drafts and required approvals are obtained again at checkout.

The local store server is the authority. Authenticated HTTPS routes enforce CREATE_CUSTOM_ORDER and store scope. Registers send no database credentials. Files are stored in protected PostgreSQL chunks with size/SHA-256 verification before a hold becomes available and again before resume. Supported limits are 5,000 cart lines, 100 files, 100 MB per file and 512 MB of attachments per cart. A successful resume claims the hold under a row lock; another register cannot resume it. The resume key supports retry after a lost response. Claimed attachment chunks are removed once the verified files have been downloaded.

A failed transfer keeps the cart. An unconfirmed hold temporarily prevents editing or closing that cart; click Hold Cart to retry or Cancel Pending Hold. Cancellation checks the server first: an incomplete transfer is deleted, while an already completed hold is confirmed instead. An unconfirmed resume retains its downloaded draft and original request key for retry.

Schema coverage: local baseline, ordered migration 20261011160000, runtime upgrade, store recovery manifest and mirror ownership rules. Historical orders and catalogue warming are unchanged; held files are fetched only when a user selects a hold.

Validation uses the disposable PostgreSQL cluster on loopback port 55443. No production database or installed application is changed by tests. A two-register check against an installed server remains a deployment acceptance step.
