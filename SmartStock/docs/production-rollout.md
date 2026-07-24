# SmartStock Production Rollout

This runbook promotes a tested SmartStock build into one Windows-based store.
The local PostgreSQL database remains the live source of truth. A separate
production Supabase project provides Auth, Storage, hosted synchronization, and
off-site recovery.

## Non-negotiable boundaries

- Set `SMARTSTOCK_ENVIRONMENT=production`.
- Production must use an explicit `SUPABASE_URL` and
  `SUPABASE_PUBLISHABLE_KEY`; the application refuses the development project.
- Registers use only the HTTPS LAN API. They receive no PostgreSQL credentials
  or Supabase server key.
- Keep the development project and production project separate.
- Never place employee passwords in a manifest, shell history, log, or file.
- Database backup does not copy Supabase Storage objects. Export and restore
  employee/product/company images and documents separately.

## 1. Prepare production Supabase

Create the production project in the Supabase dashboard. In the Server Setup
Wizard, **Connect Supabase** verifies the project URL, publishable key, and
server-only `sb_secret_` key. **Initialize Cloud** skips database credentials
when the schema is already current. For a new or outdated project, paste a
Direct or Session Pooler connection on port 5432 and enter the database
password once. Transaction Pooler port 6543 is rejected. SmartStock uses TLS,
an advisory lock, per-file transactions, checksums, and its private migration
ledger. It clears the connection and password immediately and normal operation
remains API-only.

Configure Auth redirect URLs and SMTP in the Supabase dashboard, then verify
the project with the Supabase security and performance advisors.

Use a staging or disposable project to rehearse migrations before production.
Do not apply dashboard-only production schema changes that are absent from the
repository.

Before promoting a build, run the API-only path from a configured development
server profile:

```powershell
mvn -q exec:java `
  -Dexec.mainClass=app.DevelopmentCloudSyncSmokeMain
```

The command refuses production projects, sends no credentials or row payloads
to its output, verifies the privileged schema RPC, materializes the current
store snapshot, drains cursor-based event pages, and checks the cloud mirror
row total against the local snapshot.

## 2. Provision the Windows store server

1. Install the accepted SmartStock Windows package. It includes its own Java
   runtime, so Java and Maven are not installed on the server.
2. Open **Guided Setup**, choose **Store Server**, and let SmartStock check
   PostgreSQL. If PostgreSQL 15+ is missing, click **Install PostgreSQL** and
   approve the standard Windows administrator prompt.
3. Complete the resumable six-step Server Setup Wizard:
   - **Connect Supabase**
   - **Initialize Cloud**
   - **Prepare Local Database**
   - **Create or Select Store**
   - **Create First Administrator**
   - **Start and Verify Server**
4. **Prepare Local Database** generates the private local database account,
   creates or repairs the schema, and binds PostgreSQL to loopback. If the
   PostgreSQL installer required an administrator password, enter it once in
   the fallback field. No JDBC URL, application database password, numeric
   store ID, or LAN subnet is required during normal setup.
5. **Start and Verify Server** requests Windows administrator approval,
   configures automatic startup, restricts HTTPS port 8443 to `LocalSubnet`,
   starts the service, and verifies local and cloud connectivity.

The following command is retained only as an administrator fallback when the
in-app setup cannot be opened:

```powershell
.\installer\windows\install-production-server.ps1 `
  -SupabaseUrl "https://PRODUCTION_PROJECT_REF.supabase.co" `
  -SupabasePublishableKey "PRODUCTION_PUBLISHABLE_KEY" `
  -LanSubnet "192.168.1.0/24"
```

6. Install the production `sb_secret_` key through SmartStock's server-only
   setup so Windows DPAPI protects it for the same Windows account that owns
   the scheduled task. Legacy service-role JWTs remain accepted during key
   rotation, but new installations should use `sb_secret_`.
7. Reboot Windows and verify PostgreSQL, `SmartStockServerService`, HTTPS port
   8443, and cloud synchronization return without an interactive launch.

## 3. Create the first administrator and migrate the remaining identities

After the schema and first store are ready, SmartStock opens **Create First
Administrator**:

- **Transfer Existing Administrator** lists only active Development-profile
  users whose existing role is already `ADMIN`. It copies the exact badge ID
  and verifier metadata and counts as one of the three planned transfers.
- **Create New Administrator** creates an additional production identity and
  warns that migrating all three existing users later produces four users.

The entered password is sent only to the server-side Supabase Auth Admin API.
SmartStock stores no plaintext password, source Auth UUID, session, token,
offline verifier, or badge PIN. If setup is interrupted after Auth creation, a
non-secret pending Auth UUID allows the cloud/local bootstrap to resume safely.

The first administrator must log in online once before offline login is
available. Guided Setup remains visible until that verifier exists.

For any remaining users, create their production Auth identities and use the
identity migration tooling below. It accepts a batch of one to three users; do
not include the already transferred first administrator a second time.

Copy `tools/production-identity-manifest.example.json` to the ignored
`tools/production-identity-manifest.json`. Enter the production store name,
three source usernames, and three production Auth UUIDs. Do not add passwords.

Set the source and target database connections in the operator process:

```powershell
$env:SMARTSTOCK_MIGRATION_SOURCE_DB_URL = "jdbc:postgresql://SOURCE_HOST:5432/smartstock"
$env:SMARTSTOCK_MIGRATION_SOURCE_DB_USER = "SOURCE_USER"
$env:SMARTSTOCK_MIGRATION_SOURCE_DB_PASSWORD = Read-Host "Source DB password"
$env:SMARTSTOCK_MIGRATION_TARGET_DB_URL = "jdbc:postgresql://127.0.0.1:5432/smartstock"
$env:SMARTSTOCK_MIGRATION_TARGET_DB_USER = "TARGET_USER"
$env:SMARTSTOCK_MIGRATION_TARGET_DB_PASSWORD = Read-Host "Target DB password"
```

Run the dry-run first, review the three masked identities, then confirm:

```powershell
mvn -q exec:java `
  -Dexec.mainClass=app.ProductionIdentityMigrationMain `
  -Dexec.args="tools/production-identity-manifest.json"

mvn -q exec:java `
  -Dexec.mainClass=app.ProductionIdentityMigrationMain `
  -Dexec.args="tools/production-identity-manifest.json --confirm"
```

The migration is one transaction. It copies login identity, role, store
assignment, exact badge ID, badge verifier, and DOB required by that verifier.
It copies no password, password cache, PIN, session, token, document, photo,
payroll history, or time-clock history.

Each user must then:

1. Log in online with the production account.
2. Scan the physical badge.
3. Establish a new badge PIN when required.
4. Verify role and store access.
5. Test offline login only after the successful online login.

Clear the six database environment variables from the PowerShell session after
migration.

## 4. Prove cloud recovery

Provision a separate empty local database with the complete SmartStock schema.
Its database name must end in `_recovery_drill`. Never point this command at the
live store database.

```powershell
$env:SMARTSTOCK_RECOVERY_TARGET_DB_URL = `
  "jdbc:postgresql://127.0.0.1:5432/smartstock_recovery_drill"
$env:SMARTSTOCK_RECOVERY_TARGET_DB_USER = "RECOVERY_USER"
$env:SMARTSTOCK_RECOVERY_TARGET_DB_PASSWORD = Read-Host "Recovery DB password"

mvn -q exec:java `
  -Dexec.mainClass=app.ProductionRecoveryDrillMain `
  -Dexec.args="tools/production-recovery-evidence.json 'OPERATOR NAME' --confirm-empty-target"
```

The drill refuses a target whose name lacks `_recovery_drill`, refuses a
non-empty target, restores the completed per-store cloud mirror through the
server-only HTTPS API, and compares required table counts. Separately restore a
`.ssbackup` and the exported Storage objects.

## 5. Run the go-live gate

```powershell
mvn -q exec:java `
  -Dexec.mainClass=app.ProductionReadinessMain `
  -Dexec.args="tools/production-recovery-evidence.json"
```

The gate blocks go-live unless production environment isolation, server mode,
loopback PostgreSQL, the server-only Supabase API, required shared tables,
complete materialized cloud row counts, three linked users, badge uniqueness,
empty sync backlog, resolved conflicts, and recent recovery evidence all pass.

## 6. Weekend cutover

Friday: freeze the old system, preserve its totals, install the accepted build,
enter fresh catalog/opening quantities and balances, migrate identities, pair
registers, verify peripherals, sync, and back up.

Saturday: have all three users test online/password/badge/PIN/offline access;
test every live module; reboot the server; reconcile stock, cash, external card
terminal totals, taxes, accounting, sync, and backups; obtain owner, manager,
bookkeeper, and technical sign-off.

If the server fails after cutover, use numbered manual receipts and a controlled
transaction log. Restore SmartStock, enter every captured transaction exactly
once, and reconcile before close. Never improvise direct SQL repairs during
trading.
