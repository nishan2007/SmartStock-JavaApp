# Employee web app

The employee app is an HTTPS browser gateway backed by the active store's
local PostgreSQL database. Supabase supplies account authentication only. No
PostgreSQL credentials, Supabase tokens, or privileged keys reach the browser.

## Access and activation

On the active server, a user with **Device Management** opens **Status > Employee
Web App** and selects **Start**. Once connected, enter a recognizable browser name
and generate an activation QR. Scan it on the target phone/browser and select
**Activate browser**. The QR expires after ten minutes and can be redeemed once.
Then sign in using the existing employee username/email and password.

Set the dedicated permissions under the role's **People > Employee Web** group:

- **Access Employee Web App** (`ACCESS_EMPLOYEE_WEB`): sign in and view your own
  history and calculated earnings.
- **View All Employees in Web App** (`VIEW_ALL_EMPLOYEE_WEB`): additionally browse
  employees across all stores, including deactivated employees when selected.
  Requires the access permission above.

Both permissions are seeded for Admin only. Assign access explicitly to other
roles. Desktop employee/payroll permissions do not grant employee-web access.
Account status and current permissions are checked for every authenticated request.

Session entries show **Edit** only to users with the existing **Time Clock Management**
(`TIME_CLOCK_MANAGEMENT`) permission as well as employee-web access. Corrections
remain limited to employees the viewer may see; this permission alone does not
grant the all-employee directory. All six timestamps can be corrected in the store's
timezone using the desktop workflow's validation (clock-in/out required, lunch and
break entered as complete pairs or left blank). A blank reason saves as `N/A`.
Saving recalculates hours and earnings through the existing service and appends its
normal before/after audit record. An optimistic revision check rejects stale saves;
close the editor and use Apply dates to reload before editing again.

Activation is independent of login. Sign out ends the employee session and keeps
the browser activated. Revoke a browser in the server dialog to block further
access and end all of its sessions. Clearing cookies requires a new QR. The browser
cookie lasts one year; activation records remain until revoked. Sessions expire
after 15 minutes idle or eight hours total. Stopping/restarting the gateway ends
sessions and invalidates outstanding activation grants without revoking browsers.

Existing enrolled TOTP authenticators are honored. Mandatory MFA enrollment uses
`SMARTSTOCK_EMPLOYEE_REQUIRE_MFA`, defaulting to the scheduler's
`SMARTSTOCK_SCHEDULER_REQUIRE_MFA` policy when unset. MFA tokens stay in bounded,
short-lived server memory and are tied to the activated browser.

## Hosting

Production entry point: https://timeclock.deckers.gy/ (redirects to `/employees/`).
The `cloudflare/smartstock-timeclock` Worker owns this custom domain and forwards
only employee paths through `scheduler.deckers.gy` on the existing named
`deckers-scheduler` tunnel. Its employee-only ingress connects to loopback port
8450 with the existing verified store CA. Scheduler and careers ingress rules
remain in place. Responses are never cached. POS-SERVER sets
`public.origin=https://timeclock.deckers.gy` in the service account's
`.smartstock/employee-web.properties`; the managed employee runtime starts from
the persisted console switch without launching a Quick Tunnel.

The installed background server and standalone RemoteGatewayMain both manage the
listener, disabled by default. The listener binds only to `127.0.0.1:8450` using
the store TLS identity. The port can be overridden with the JVM property
`smartstock.employee.web.port`.

For permanent hosting, configure `SMARTSTOCK_EMPLOYEE_PUBLIC_ORIGIN` or
`public.origin=https://your-host` in `~/.smartstock/employee-web.properties` on the
service account. Proxy **only `/employees` and `/employees/*`** to this listener,
preserving path and query, and set the trusted client-address header at the proxy.
Use a certificate-verified origin connection with the store CA, or a connector
restricted to the same machine, following the scheduler deployment convention.
Keep PostgreSQL and the register `/v1/` API private. Restrict external firewall
access to the gateway port. Do not cache employee HTML or API responses.

Without a configured origin, the existing bundled Cloudflare Quick Tunnel client
provides a temporary HTTPS address. That address can change after restart; use
the current link shown in SmartStock. No public tunnel is started until an
administrator starts the app. `/employees/health` checks the active primary and
enabled runtime generation. In-store browsers can use the same public address.

Apply the ordered local migrations through normal SmartStock schema provisioning.
Only the permission migration belongs in the cloud migration chain. Browser grants,
credentials, sessions, and runtime state are local-only and are excluded from
reference synchronization. No installer secrets or extra Windows service are needed.

## History behavior

Managers land on the searchable directory; employees land on their own history.
The default date range is the selected employee's current pay period. Date ranges
filter original session work dates, newest sessions first, in pages of 50. Select
another range (up to 367 days) for older history. Timestamps show store-local time
and timezone, including overnight dates.

Calculations reuse the desktop payroll engine with the employee's complete history
before filtering/pagination, including cross-store overtime, effective pay rates,
daily pay, and half days. Overnight calculation segments are summed once into the
original session. Lunch and break deductions match existing payroll rules.
Missing timestamps, open/provisional sessions, and automatic closure review states
are visible. Salary is a period amount and is explicitly not allocated to sessions.
Calculated session earnings do not indicate whether payroll has been paid and do
not include period-level bonuses or deductions.

## Verification

Run normal Maven tests, the Bash security check, and `git diff --check`.
Run `node SmartStock/tools/test-employee-web.mjs` from the repository root with
Playwright installed to check the UI against synthetic fixtures; on Windows set
`PLAYWRIGHT_CHANNEL=msedge` to use installed Edge. Screenshots are written to
`SmartStock/target/employee-web-ui/`. This check never contacts a store or Supabase.
`EmployeeWebDatabaseTest` additionally supports a disposable PostgreSQL cluster:
`-Dsmartstock.employee.test.jdbc=jdbc:postgresql://127.0.0.1:55443/postgres`, user
`employee_web_test`, trust authentication. It uses temporary fixtures and never
connects to a configured store database.

Before deployment, separately check the installed Windows service, permanent
proxy/certificate routing, real-phone QR redemption, cellular login/MFA, role
changes, browser revocation, and parity with the store's payroll screen. This work
does not install or publish a release or change a live database.
