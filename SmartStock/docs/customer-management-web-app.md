# Customer Management web app

Target address: https://customermanagement.deckers.gy

Lists all customer accounts and searches by name, account number, phone or email. An opened account shows the credit summary and the latest ten ledger transactions. History search additionally matches older receipts, orders, item descriptions, and payment references. Select a sale for its receipt and select an order for a full order sheet with items, payment, fulfillment and audit details. Private file downloads use the same custom-order permissions and retention as desktop downloads and are served by the originating store. The account editor uses the desktop Customer Accounts permission and the existing field permissions for account number and credit limit. Concurrent profile edits return a conflict. Reopen the editor before retrying.

Record Payment and Add Charge use the active store’s customer ledger, with server-side allocations, permissions and retry protection. Cash payments require an open drawer assigned to the signed-in employee at that store.

The app uses the existing employee_web_devices approval registry, activation links, revocation, employee sign-in and configured MFA. CUSTOMER_ACCOUNTS is required; employee/payroll access is not granted. Every customer request checks active-store membership. Existing approval cookies are host-only: paste a fresh unused activation link from the existing Web App Device Management into Activate this browser on the new hostname. This creates an approval in the same registry; it cannot silently transfer a browser cookie from another hostname.

PostgreSQL remains the live authority. Remote access passes through the existing employee tunnel. The store server, gateway, database and internet connection must remain online. No customer records are stored in browser storage. Private screens clear when backgrounded and revalidate on return.

## Deployment

Prepare assets with node SmartStock/tools/prepare-customers-web.mjs, then deploy with wrangler deploy --config SmartStock/cloudflare/smartstock-customers/wrangler.json. The custom domain is declared in that configuration. Deploying the Worker does not update the installed store application; install the updated Java gateway through the approved packaging workflow. Run the local schema upgrade for customer adjustment retry records. Apply the cross-store order snapshot cloud migration through the existing cloud schema process before enabling full order details from other stores. Older snapshots still supply their available history summaries until a new snapshot is synchronized. The customer app reads the active store’s PostgreSQL database and synchronized local copies.

## Verification

Run CustomerWebAccessTest and EmployeeWebSecurityTest with Maven, node --test SmartStock/cloudflare/smartstock-customers/test/gateway.test.mjs, the Bash security check, and git diff --check. Before live use separately verify installed Windows server/service behavior, remote mobile-data activation/login/MFA, revocation, role and store membership changes, real customer creation/editing, and concurrent desktop edits. Database integration and browser acceptance remain required. This app adds no printer, drawer or NFC operation.

## Deployment status — October 29, 2026

Worker version 8ab6877d-9fc8-4189-bdb9-4c7471ffc601 is deployed on the custom domain. All three live assets match source download sizes and SHA-256 hashes. Unapproved session and customer requests return DEVICE_REQUIRED. At the time, the live health probe returned SERVER_UPDATE_REQUIRED for the installed Java store server. No live store installation was performed.
