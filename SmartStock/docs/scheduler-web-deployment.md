# Scheduler web deployment

The scheduler web app runs with the installed SmartStock background server service.
`app.RemoteGatewayMain` is an optional standalone alternative, not a second service
to run alongside it. A database advisory lock elects one scheduler controller. It listens on
`https://127.0.0.1:8446` by default and is intended to be reached through a
public reverse proxy at `/scheduler/`. PostgreSQL on the active store server
remains authoritative; remote scheduling is unavailable while that server or
its internet connection is offline.

## Before deployment

1. Before updating or applying migrations, stop store writes in a maintenance
   window and take a complete PostgreSQL backup plus the existing SmartStock
   configuration/recovery backup. Restore the database backup to an isolated
   database and verify it before proceeding. Keep backups outside the updater's
   application rollback folder and protect them as sensitive store data.
2. Give only the owner role `ACCESS_SCHEDULER_WEB`. It also needs
   `VIEW_EMPLOYEE_SCHEDULE`; editing requires `EDIT_EMPLOYEE_SCHEDULE`, and
   other-store access requires `SCHEDULE_OTHER_STORES`.
3. Scheduler sign-in is email/password only by default, per the owner's revised
   rollout decision. To require TOTP later, set `SMARTSTOCK_SCHEDULER_REQUIRE_MFA=true`
   and restart the scheduler. This setting never removes existing Supabase factors.
   The SmartStock user must have the same `auth_user_id` for normal deployment.
4. Set the production Supabase URL and publishable key on the server. Never put
   a secret or service-role key in the browser or reverse-proxy configuration.
5. For a permanent hostname, set `SMARTSTOCK_SCHEDULER_PUBLIC_ORIGIN` to the
   exact HTTPS origin, such as `https://scheduler.example.com`. For temporary
   operation, leave it unset; the Windows package bundles a pinned, SHA-256-checked
   `cloudflared` client under `dependency/cloudflared/windows-amd64`. SmartStock will create
   and display a random `trycloudflare.com` address whenever the web app starts.

## DNS, TLS, and proxy

Point the chosen DNS name at the HTTPS reverse proxy. Install a publicly trusted
certificate there. Proxy `/scheduler/` and `/scheduler/health` to loopback port
8446 using HTTPS, preserve the `Host` header, limit request bodies to 1 MiB, and
apply an additional per-IP login rate limit. Do not proxy port 8443 or any
`/v1/` register route to the public internet. Keep PostgreSQL and ports 8443 and
8446 blocked at the external firewall.

Start the installed SmartStock background server service with the normal local
PostgreSQL configuration. Only the standalone `RemoteGatewayMain` alternative
requires `SMARTSTOCK_GATEWAY_MODE=REMOTE_ADMIN`. The scheduler is disabled by default.
On the active store server, open **Status > Scheduler Web App** and select
**Start Web App**. Confirm `/scheduler/health` returns `{"ok":true}`, then test
login and one schedule mutation over cellular data.

## Operations and rollback

Monitor gateway availability, failed login audit events, TLS expiry, database
health, and store internet connectivity. To revoke access immediately, remove
`ACCESS_SCHEDULER_WEB` from the role; permissions are checked on every request.
Selecting **Stop Web App** on the active server revokes all scheduler browser
sessions immediately, stops the listener within a few seconds, and persists the
off state across gateway and SmartStock restarts.

An account-free Quick Tunnel is for temporary use only. Its address changes
after the tunnel or gateway restarts and has no availability guarantee. The
Status dialog always shows the address assigned to the currently running tunnel.
To roll back, stop scheduler access and store writes first. The updater's binary
rollback is NOT a database backup. Older application versions may reject the
new schema fingerprint; do not assume a binary-only downgrade is compatible.
Prefer a forward repair. If a full database restore is necessary, preserve a
new backup of the failed state and reconcile any transactions recorded since
the pre-update backup before restoring. Never automatically restore over live
store data. Keep session/audit evidence protected with the recovery backup.

## Required release acceptance

### Optional isolated production-login test

For explicitly authorized testing only, `SMARTSTOCK_SCHEDULER_TEST_PRODUCTION_AUTH=true`
lets the scheduler alone use the saved production **public** Supabase configuration.
It requires development mode, a loopback `smartstock_dev` database (optional suffix),
and explicit `SMARTSTOCK_SCHEDULER_TEST_PRODUCTION_SUBJECT` and
`SMARTSTOCK_SCHEDULER_TEST_DEVELOPMENT_SUBJECT` UUIDs. Only that production subject
is accepted and it maps to the existing test user; no user identity rows or passwords
are rewritten. The test user's active status and scheduling permissions still apply.
Other gateway features retain their development authentication configuration.
Unset all three test variables before normal deployment. Never configure global
`SUPABASE_URL` overrides merely to test scheduler sign-in.

### Acceptance checks

Do not publish based only on unit tests. Require the exact Windows update archive
to contain the verified tunnel client, rehearse an installed service update and
restart, and verify backup restoration. Then test password login (and MFA if enabled), schedule edits,
Auto Schedule preview/apply, permission revocation and Stop Web App from a phone
on cellular data. Verify the current link in Status after a tunnel restart.
Temporary Cloudflare tunnels are a test/temporary service, not a permanent
availability guarantee.

The opt-in `SchedulerMigrationRecoveryIntegrationTest` uses only the explicitly
selected local development database, backs it up and restores it into a random
scratch database, then checks that scheduler migration, rollback and replay do
not change business rows. This does not replace a production backup rehearsal.
