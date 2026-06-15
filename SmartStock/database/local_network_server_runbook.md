# SmartStock Local Network DB Server Runbook

## Store Server

Use one always-on mini PC per store.

1. Install PostgreSQL 15+.
2. Create the local database and app roles.

```sql
CREATE DATABASE smartstock;
CREATE ROLE smartstock_server LOGIN PASSWORD 'replace-with-server-password';
CREATE ROLE smartstock_client LOGIN PASSWORD 'SmartStockClientLan2026!';
GRANT ALL PRIVILEGES ON DATABASE smartstock TO smartstock_server;
```

3. Open SmartStock, choose **Database Setup**, fill in the server settings, then click **Provision Server**.

Provision Server will create the configured local database when PostgreSQL is reachable, create or repair the built-in `smartstock_client` role, grant it local app-table access, configure PostgreSQL for LAN clients, install the local sync and employee credential tables, verify cloud sync tables when cloud credentials are present, save the config, and start the in-app sync worker.

If you use the macOS installer, it also saves the generated database credentials here:

```text
~/.smartstock/database-credentials.txt
```

The server app uses `SMARTSTOCK_DB_USER` / `SMARTSTOCK_DB_PASSWORD`.
Register clients use the built-in SmartStock client credentials automatically.

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

Each register connects only to the store server.

1. Open SmartStock, choose **Database Setup**, and set:

- Mode: `CLIENT`
- Server IP / Hostname: `<store-server-hostname>.local`
- Local DB User/Password: filled automatically by SmartStock
- Leave cloud credentials blank

2. Test the local connection.
3. First employee login must happen while cloud auth is reachable; that successful login caches the employee's password verifier on the local server for offline use.
4. After successful online login, optionally create the employee PIN when prompted.

## Operational Checks

- Use **Sync Status** on the server to check cloud online/offline state, pending events, failed events, and open conflicts.
- If cloud is down, POS cash flow can continue locally.
- When cloud returns, the server sync worker uploads local outbox events to the cloud sync tables for idempotent processing/review.
- Open conflicts must be resolved by a manager; the app does not silently overwrite shared records.

## Current v1 Boundary

This implementation records durable transaction-level events and uploads them to cloud sync tables. The next production-hardening phase should add per-event replay handlers that materialize each uploaded event into the cloud business tables with `sync_id_map` translation.
