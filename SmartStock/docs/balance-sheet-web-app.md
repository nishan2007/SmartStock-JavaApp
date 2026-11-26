# Balance Sheet web app and input history

Public address: **https://balancesheet.deckers.gy**. The dedicated Cloudflare
Worker serves only Balance Sheet assets and forwards allowlisted activation,
authentication and Balance Sheet API requests through the existing store tunnel.
The store's employee HTTPS gateway owns the backing `/employees/balance-sheet.html`
and API implementation. Local PostgreSQL remains authoritative.
Use the existing approved-browser activation, employee sign-in and MFA flow.
Sign-in for this app requires `BALANCE_SHEET`; `ACCESS_EMPLOYEE_WEB` is not
required and Balance Sheet access does not grant employee/payroll app access.
An existing approved browser on the same domain stays approved after a server
update. Moving from timeclock.deckers.gy to balancesheet.deckers.gy requires
activation once on the new hostname because approval cookies are host-scoped.
Use a direct activation QR or paste a fresh unused activation link from the
existing Employee Web App screen into **Activate this browser** on the new site.
The token is redeemed on the new domain, without navigating to the pasted URL.
No separate public register API or database connection is exposed. Deploying
this Worker does not install or restart the Java store application.

## Workflows

Current Sheet includes desktop calculations, drawer selection, expenses/payables,
Other cash income, payable payments, cheque deposits, overseas cash withdrawals,
administrator B/F adjustments and legacy cash recovery. Date changes open a
separate stable workspace, including the sorted drawer-session selection.
Print / Save PDF uses the browser print dialog; CSV exports the sheet sections.
Saved Sheets opens submissions and the existing latest-only, 48-hour revision
editor. Submission/revision email copies use the existing outbox and settings;
an email-queue failure does not reverse a successfully saved accounting change.
After submission, the current sheet advances to the next accounting period.

History requires `VIEW_BALANCE_SHEET_HISTORY`, initially granted to Admin only.
Grant it explicitly to managers. The browser history sheet selector includes
older date/drawer workspaces. Filter by employee name, store-local receipt date,
or event type; load older pages without skipping records. Open an event to see
before/after differences and its complete snapshot; select two events to compare.
The desktop offers Entry History for the selected workspace and preserves its
existing submission revision and drawer-history workflows.

## Capture guarantees

Both interfaces capture raw text after a 500 ms pause, at most two seconds into
continuous typing, and on field exit. Add/remove/clear/cancel events are immediate.
Invalid amounts and abandoned rows remain in history and never become accounting
transactions merely because they were captured. Each form and editable row has
an identity. Posting links the captured form to returned accounting IDs and/or
the submission. A submitted/revised snapshot is retained with the journal event.

The server records employee ID/name, device identity, server receipt time, client
capture time, revision, action, and complete reconstructable snapshots. Automatic
accounting refreshes are labeled separately with no employee attributed to them.
Existing saved submissions/revisions remain available; earlier typing cannot be
reconstructed retroactively.

Web draft captures use an ordered per-user browser queue, with one storage entry
per idempotency key to avoid overwriting another tab's unacknowledged events.
Acknowledged entries are removed. Interrupted accounting saves retain their
original request and key; use **Check previous accounting saves** to confirm the
original result before entering another transaction. Conflicting draft captures
can be retained as a separate entry, linked to the original conflicting event.
Changing an accounting payload after an uncertain save requires checking that
save first. Queues are scoped to the signed-in employee and retained across
session expiry; private screens close on expiry/revocation.

Desktop captures are serialized off the Swing event thread and retained in the
open window's retry queue. An unavailable journal blocks the associated financial
save. Status labels distinguish saved input from unsent input. Do not close an
offline desktop window containing unsent input: its queue is in memory.

No interface can guarantee receipt of input lost before the server acknowledges
it. Store/network outages disable accounting commits. Browser storage removal,
device loss, or an application crash can lose unsent input. Client capture times
are informational; the server receipt time and revision order are authoritative.

## Data and recovery

The local PostgreSQL store is authoritative. New workspace, draft, event and
snapshot tables are installed by the local baseline and ordered migration
`20261025160000_balance_sheet_workspaces.sql`, including existing-store upgrades.
Cloud migration `20261025160100_balance_sheet_history_permission.sql` carries
the role permission only; no browser account receives database/server credentials.

Financial changes and their audit event commit in the same transaction.
Retries check employee/device, request key and payload hash. Stale workspace or
form revisions fail explicitly. Permission, active account, store membership,
admin restrictions and drawer ownership are checked server-side. Draft captures
do not overwrite another employee/device's form.

Event and snapshot tables reject update/delete/truncate, and ordinary server
role grants exclude those operations. Snapshot descriptors reference deduplicated
sheet sections and draft blocks. Durable workspaces/events/blocks participate in
the existing store-scoped off-site mirror; mutable draft rows stay local. Full
company backups include all four tables. The physical-server owner restore path
temporarily disables user triggers inside its restore transaction and reenables
them before commit. Application clients have no restore endpoint in this app.
No automatic history retention deletion is configured.

## Dedicated-domain deployment

```powershell
node SmartStock/tools/prepare-balance-sheet-web.mjs
wrangler deploy --config SmartStock/cloudflare/smartstock-balance-sheet/wrangler.json
node SmartStock/tools/verify-balance-sheet-web.mjs
```

The Worker binds `balancesheet.deckers.gy` as a Cloudflare custom domain and uses
`scheduler.deckers.gy` for the existing employee ingress. It validates the exact
public Origin before forwarding with the employee listener's configured Origin.
Host-only secure HttpOnly cookies remain local to the Balance Sheet hostname.
Employee/payroll, Orders, Inventory and register API routes are not exposed.
The icon, manifest and all assets are verified by exact download size and SHA-256.
The frontend remains available when the store is offline, with store operations
reporting unavailability rather than showing cached accounting data.

On an updated desktop, open **Balance Sheet → Pair Balance Sheet Device** to
issue a QR aimed at the dedicated hostname. The existing **Status → Employee
Web App…** activation screen can issue a fresh link for the paste flow.
Pairing retains the existing Device Management and server-console checks;
normal Balance Sheet sign-in needs only Balance Sheet permission.

Deployed October 26, 2026 as Worker version
`355d96cc-f994-4477-ba92-fcb16322914b`. Live verification passed for the HTTPS
hostname, all eight assets, store health, activation requirement and route isolation.
This deployment did not install the desktop pairing-button source changes.

## Verification

- Run Maven tests, the Bash security check, and `git diff --check`.
- `node SmartStock/tools/test-balance-sheet-web.mjs` uses synthetic browser
  fixtures only. Set `NODE_PATH` to available Playwright packages and, on Windows,
  `PLAYWRIGHT_CHANNEL=msedge`. Screenshots go to `SmartStock/target/balance-sheet-web-ui/`.
- `BalanceSheetWorkspaceDatabaseTest` runs only with
  `-Dsmartstock.test.balanceSheetJdbc=jdbc:postgresql://127.0.0.1:55449/postgres`,
  user `balance_sheet_test`, against a dedicated disposable PostgreSQL cluster.
  It replaces that cluster's public schema. Never point it at a store database.
- Database cases cover invalid/abandoned typing, retries, stale saves, device and
  store isolation, permission enforcement, append-only history, audit failure
  rollback, automatic refresh, pagination, submission/revision snapshots,
  48-hour expiration, off-site ownership manifests, and full backup restoration.

Before deployment separately verify the installed Windows service/schema upgrade,
real remote phone activation/login/MFA, revocation, concurrent web/desktop editing,
reconnect after restarting the store service, configured email delivery, existing
store reconciliation totals, real printer output and a recovery drill. Drawer,
printer, cash-drawer hardware and NFC behavior are not proved by these tests.
