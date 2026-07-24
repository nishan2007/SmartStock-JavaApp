# SmartStock LAN Service Runbook

Registers connect only to the HTTPS SmartStock Server Service on port `8443`,
never to PostgreSQL. This pre-launch release has no compatibility database role
or mixed-client mode.

## Store Server

Use one always-on mini PC per store.

1. Open SmartStock and choose **Guided Setup > Store Server**.
2. Complete the six-step Server Setup Wizard. It checks or installs PostgreSQL
   15+, generates the local `smartstock_server` role and password, creates or
   repairs the database, restricts PostgreSQL to loopback, and installs the
   SmartStock Server Service.
3. If PostgreSQL asked for an administrator password during installation,
   enter it once in **Prepare Local Database**. SmartStock immediately clears
   that field and securely stores only its generated application credential.
4. Use **Advanced Settings** only for repair or diagnostics. Registers never
   receive local database credentials.

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

For diagnostics, the resulting server configuration should be:

- Mode: `SERVER`
- Local JDBC URL: `jdbc:postgresql://127.0.0.1:5432/smartstock`
- Local DB User/Password: server role credentials
- Normal cloud operation: Supabase HTTPS API only
- Sync interval: `300` seconds by default

The following source-tree launch command is for development diagnostics only:

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

## Production Recovery Boundary

Current sync materializes the supported operational tables into the hosted
database and retains the durable event outbox for idempotency/audit. Do not
infer backup readiness from a successful sync status alone. Before production
go-live, provision a separate database whose name ends in
`_recovery_drill`, restore from the hosted project with
`ProductionRecoveryDrillMain`, and require the resulting evidence file in
`ProductionReadinessMain`. Storage objects require a separate export/restore
check because Supabase database backups contain Storage metadata, not the
objects themselves.
