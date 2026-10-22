# SmartStock 1.1.33 Windows

Build 101033 adds explicit half-day classification for daily-rate employee work
dates, from Payroll Work Sessions and the employee Time Clock after clock-out.
Multiple sessions share one date-level choice. Marked dates pay 50% of the daily
rate; unmarked dates pay the full rate. The server enforces employee ownership,
completed sessions, store ownership and protection of payroll periods with payments.

This build also includes the LAN HTTPS transport correction documented in
[LAN HTTPS DNS stall](lan-https-dns-stall.md).

Validation: packaging Maven gate ran 1,026 tests, 61 skipped, no failures/errors.
The preceding half-day gate also passed the dedicated disposable PostgreSQL
integration test (1,026 tests, 60 skipped, no failures/errors). Repository security
and whitespace checks passed. Model-free updater validation and hosted catalogue
readiness passed. All 30 updater entries match the extracted all-in-one installer;
its native Windows launcher opened the welcome window in an isolated development
register profile and the test process was stopped.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 166962440 | d76236e3721e5f79aa517fda67768dce57dc7b4755d047daa425056d2d4313be |
| All-in-one installer | 580035379 | a039b16cbb8998a00419d8f7c40aff0c93ebb2f54cc3bea6b29dbcc8debd78ac |
| Model migration helper | 3318 | d133a3ea5954103ef70b346de34ffe4bb77ff2f1cebb35578e1a89ea99906c1d |

Publication completed. Both uploaded artifacts were downloaded in full and their
exact byte sizes and SHA-256 values verified before publication. Update metadata
readback matched version 1.1.33, build 101033, published status, content-addressed
object path, byte size and SHA-256. The public installer HEAD check returned
HTTP 200, the correct 1.1.33 filename and 580035379 bytes at
https://downloads.deckers.gy/download/windows.
These artifact bytes must not be rebuilt under this published version.

No live application was installed or restarted. Live payroll-screen acceptance,
database/service migration acceptance, register connectivity, update/rollback and
printer, drawer and NFC checks remain outstanding. Update the store server before
registers. macOS requires separate packaging and verification on a Mac. SmartStudio
was not packaged or published with this release.
