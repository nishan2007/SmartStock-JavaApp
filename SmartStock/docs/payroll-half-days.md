# Daily-rate half days

Daily-rate work dates pay the full daily rate unless explicitly marked as a half
day. A marked date pays 50% of the daily rate, using the application's whole-GYD
rounding. Worked hours remain unchanged. Hourly and fixed-salary pay are unchanged.

In Payroll, double-click the employee's payroll summary to open Work Sessions.
Tick or clear **Half Day** beside a completed daily-rate session. The selection
applies to all sessions for that employee on that calendar work date; it never
halves the daily rate repeatedly. Session pay and the payroll summary refresh
after saving. Payroll access is required by the server.

On the employee Time Clock screen, **Half Day** appears after clock-out for
daily-rate work completed today in the store timezone. Clicking it saves the same
date-level choice and changes the button to **Half Day Marked**. Employees can
mark only their own completed day at the store where they worked. Payroll users
can clear a mistaken selection. Once a payroll period has any payment recorded,
its day classifications cannot change.

The local schema migration is
`20261015120000_daily_half_days.sql`; it is included in the baseline and registered
server upgrade path. Date choices are covered by recovery manifests and shared
reference synchronization, with location ownership enforcement. No register
database credentials are required.

Verification includes `DailyPayDayServiceTest` and the opt-in
`DailyPayDayDatabaseTest`. The latter requires a disposable PostgreSQL cluster at
`jdbc:postgresql://127.0.0.1:55457/postgres` with user `half_day_test`; enable it with
`-Dhalfday.test.jdbc=jdbc:postgresql://127.0.0.1:55457/postgres`. It creates and removes
its own database and never reads the application database configuration.

Before deployment, verify the two controls in the installed Windows application
against the intended LAN server. Source tests do not verify the installed app.
