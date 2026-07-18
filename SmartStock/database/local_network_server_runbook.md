# SmartStock LAN Service Runbook

Registers connect only to the HTTPS SmartStock Server Service on port `8443`,
never to PostgreSQL. This pre-launch release has no compatibility database role
or mixed-client mode.

## Store Server

Use one always-on mini PC per store.

1. Install PostgreSQL 15+.
2. Create the local database and app roles.

```sql
CREATE DATABASE smartstock;
CREATE ROLE smartstock_server LOGIN PASSWORD 'replace-with-a-unique-generated-server-password';
GRANT ALL PRIVILEGES ON DATABASE smartstock TO smartstock_server;
```

3. Open SmartStock, choose **Database Setup**, fill in the server settings, then click **Provision Server**.

Provision Server creates the local database, installs the LAN service/session
schema, verifies cloud sync tables when configured, saves server-only database
credentials, restricts PostgreSQL to the server machine, disables any legacy
register roles it finds, and installs the SmartStock Server Service.

If you use the macOS installer, it also saves the generated database credentials here:

```text
~/.smartstock/database-credentials.txt
```

The server app uses `SMARTSTOCK_DB_USER` / `SMARTSTOCK_DB_PASSWORD`.
Registers store only the pinned LAN certificate fingerprint and their unique,
revocable API credential in Keychain or Windows Credential Manager.

To repair an older local install that saved placeholder labels such as `SMARTSTOCK_DB_USER`, rerun:

```bash
SmartStock/installer/macos/install.command server
```

That keeps the local database, repairs roles/passwords/grants, rewrites `database.properties`, and refreshes `database-credentials.txt`.

Only reset the local database when you explicitly want to delete and recreate it:

```bash
SMARTSTOCK_CONFIRM_RESET=YES SmartStock/installer/macos/install.command server --reset-local-db
```

If you need to apply the sync tables manually, run:

```bash
psql postgresql://smartstock_server@localhost:5432/smartstock -f SmartStock/database/local_network_sync_setup.sql
```

4. On the server machine, **Database Setup** should be:

- Mode: `SERVER`
- Local JDBC URL: `jdbc:postgresql://127.0.0.1:5432/smartstock`
- Local DB User/Password: server role credentials
- Cloud JDBC URL/User/Password: Supabase Postgres credentials
- Sync interval: `60`

5. Start the server app with:

```bash
java -cp target/classes:target/dependency/* app.Main --server
```

## Register Clients

Each register connects only to the store's HTTPS SmartStock Server Service.

1. Start SmartStock in `CLIENT` mode. It discovers the server automatically when
   UDP broadcast is available; otherwise enter the server hostname during installation.

- Mode: `CLIENT`
- Server IP / Hostname: `<store-server-hostname>.local`
- LAN service port: `8443`
- Leave cloud credentials blank

2. An administrator chooses **Pair This Register** and enters the temporary
   phrase shown under **Device Management > Security Status** on the server.
3. Approve the displayed register name and store once. The register claims and
   rotates its credential silently; employees never enter the phrase.
4. First employee login must happen while cloud auth is reachable; that successful login caches the employee's password verifier on the local server for offline use.
5. After successful online login, optionally create the employee PIN when prompted.

## Lockdown Verification

From the repository root on the physical macOS server:

```bash
SmartStock/tools/lan-api-cutover-check.sh
SmartStock/tools/activate-lan-api-cutover.sh --confirm
```

The activation command is an idempotent physical-server verification and
lockdown step. It refuses to proceed while any register-callable JDBC path
remains, disables any legacy database roles left by development installs, binds
PostgreSQL to loopback, removes obsolete register DB secrets, and sets
`lan.api.enforced=true`.

Emergency physical-server recovery is intentionally explicit:

```bash
SmartStock/tools/recover-lan-api-cutover.sh --physical-server-confirm
```

## Operational Checks

- Use **Sync Status** on the server to check cloud online/offline state, pending events, failed events, and open conflicts.
- If cloud is down, POS cash flow can continue locally.
- When cloud returns, the server sync worker uploads local outbox events to the cloud sync tables for idempotent processing/review.
- Open conflicts must be resolved by a manager; the app does not silently overwrite shared records.

## Current v1 Boundary

This implementation records durable transaction-level events and uploads them to cloud sync tables. The next production-hardening phase should add per-event replay handlers that materialize each uploaded event into the cloud business tables with `sync_id_map` translation.
