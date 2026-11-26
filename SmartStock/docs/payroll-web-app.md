# Payroll in Deckers

Payroll is a mounted screen inside `deckersapps.deckers.gy`, using the existing
employee session, MFA, CSRF, and device approval. Managers enable **Payroll** for
the device in Mobile Management. The employee must also have **Payroll Dashboard**
(`PAYROLL_DASHBOARD`) permission. Both checks run again for protected requests;
access to Employee History alone does not grant payroll access.

The selected layout places the app heading in the shared plum masthead, with the
store and pay-period selectors immediately below the Home control. Home, Account,
and all mounted app screens use the shared heading. Redundant child branding is
hidden; workflow titles, dialog titles, status information and toolbar actions
remain available.

## Stores and pay periods

Outside the store network, the selector defaults to **All stores**. A direct HTTPS
LAN challenge, bound to the device, employee, app and origin, can default it to
the current server's store. The public gateway cannot verify that challenge.
An unavailable or expired network proof leaves All stores selected and does not
prevent off-site payroll use. The manager can change the selection manually.

Store selection filters employees who worked at that store during the selected
period. The employee's hours, calculated pay and payment balance cover the entire
pay period across stores, matching the desktop payroll journal. A visible note
explains that scope when a store is selected. The same wages therefore do not
acquire separate payable balances when the manager switches stores. The payroll
permission has the same cross-store visibility as the desktop Payroll Dashboard.
The selector does not redirect accounting writes: the authenticated local store
server remains the payment's accounting origin.

The server returns available periods and employee details only for the selected
period. Calculation, historical rates, overtime, bonuses, settled snapshots and
cash rounding continue to use `ServerTimeClockManager`.

## Recording payments

Expand an employee, enter the exact whole-GYD amount actually paid and the payment
date, then save. The amount is recorded without rounding the entered payment.
The preview uses the existing desktop cash settlement rule. Cash in hand is the
default; expand its selector to choose Bank account and enter the bank reference.
This records an existing payment; it does not initiate a bank transfer.

Partial payments leave a remaining balance and show Part-paid. Settled periods
show Paid. Payment dates apply to both the payroll payment and its cash expense
or bank transaction. Future dates, non-positive/fractional amounts, missing bank
references and payments above the allowed balance are rejected on the server.

Payment requests use a persisted retry key and hash in `payroll_web_requests`.
An exact retry returns the earlier result. Employee-row locking and a fresh
paid-amount comparison reject stale submissions. The retry row, payroll payment,
and accounting entry share one database transaction. Unsaved details remain after
errors; switching screens warns before discarding them. No pay information is
added to the employee self-service Account projection.

## Schema and verification

The local baseline and `20261121140000_payroll_web_requests.sql` provision the
server-only retry table, primary key and created-at index with RLS. A guarded,
transactional upgrade handles existing local stores and updates their schema
contract. LAN provisioning uses the same upgrade. The retry table is runtime
state rather than cross-store reference data, so it is not mirrored to cloud.
Payroll payment synchronization continues through the existing journal path.

`PayrollWebDatabaseTest` runs only on an explicitly selected disposable loopback
cluster. It checks fresh installation, guarded upgrades, permissions, cross-store
employee totals, partial/full payments, cash/bank accounting, dates, exact retries
and stale requests. The isolated browser fixture checks all screen headers,
store defaults, period switching, payment entry and responsive widths.

No live application, server, device grant or payroll record was changed by local
implementation and verification. Real iPhone AutoFill, LAN certificate trust and
installed-server acceptance remain deployment checks.
