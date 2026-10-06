# Storefront release check — 2026-09-26

Status: candidate includes Website Status controls and branding cleanup but predates
the top-navigation department dropdown; rebuild before release. Update unpublished;
domain not activated.

## Verified

- Full Maven test suite and packaging tests passed.
- Explicit two-database StorefrontDatabaseIntegrationTest passed on disposable
  loopback PostgreSQL port 55439 (zero skipped).
- Worker: 22 tests passed. Frontend: 4 tests passed; compiled bundle gate passed.
- Bash security check and git diff --check passed.
- Default and deckers.gy production Worker dry-run builds passed.
- Cloudflare authentication works; deckers.gy is an active zone. The existing
  healthy tunnel was deckers-scheduler. A separate storefront tunnel has since
  been created; see the tunnel setup record below. No storefront Worker is deployed.
- Read-only release lookup found Windows 1.0.218/build 100218 already published.
- Branding cleanup retains one header logo (with accessible company-name fallback),
  shows saved motto lines only in the footer, and deduplicates contact/address lines.
  The header department selector opens the catalog with that department selected;
  All departments clears the category filter. Frontend build and four tests passed.
- Latest desktop browser check: top-navigation Departments dropdown rendered;
  keyboard activation and Stationery selection correctly showed one matching
  synthetic product and closed the list. Frontend build and four tests passed.
  The synthetic preview listener is on loopback 5180; never publish this preview.

## Exact candidate

Directory: `target/storefront-release-check-ced7be32c9a249038704a2f02a2a40be`.
See `verification.json` there. ZIP root JAR/dependency layout was checked for the
existing in-app updater. Packaged website resources and storefront SQL were
compared byte-for-byte with the current files. The candidate includes concurrent
workspace changes, including catalog photo gallery work.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| smartstock-windows-1.0.218.zip | 57814908 | c78e35632f8bf31bf3bab4845966c8033977e345b3b7ce92f4d08779d4034b96 |
| smartstock-windows-setup-1.0.218.exe | 70186368 | e9e77186cd73f97c32c0bd9be926579b6bffccd3c8f1e50d393ea12728caa1da |

This is not a new updater release: its version is already published. Do not
overwrite that release. Allocate a newer version and rebuild after acceptance.

## Domain preparation

`cloudflare/smartstock-storefront/wrangler.production.jsonc` targets www.deckers.gy
as the main Worker custom domain, with deckers.gy redirecting GET/HEAD navigation
to www while preserving path and query. POST requests on the redirect hostname
are rejected, so account/order bodies are not redirected across hosts. Both
domains have workers.dev disabled. This file is not deployed. All 23 Worker tests
and the updated production dry-run passed.
ORIGINS_JSON is intentionally empty until verified registered store-instance IDs
and permanent named Tunnel hostnames are available. Keep the scheduler tunnel
unchanged. Never publish the synthetic port-5180 preview.

The store listener requires SMARTSTOCK_STOREFRONT_ORIGIN=https://www.deckers.gy,
its distinct edge secret and a shared session-encryption key. The Worker needs
matching ORIGIN_KEYS_JSON secrets. Each tunnel must target loopback HTTPS 8449
with certificate validation, never PostgreSQL or the register listener. Merely
applying the in-app update does not set those service environment values.

## Tunnel and status-monitor follow-up

On September 26, the dedicated `deckers-storefront` remote-managed tunnel was
created and configured for `storefront-origin.deckers.gy` to reach
`https://127.0.0.1:8449`. Origin certificate verification is enabled with
`originServerName=localhost` and a local public certificate trust file. Unmatched
hosts receive HTTP 404. Scheduler and careers tunnel configuration was unchanged.

A separate hidden cloudflared process established four QUIC connections. It uses
a CurrentUser DPAPI-protected token outside this repository. No Windows service
was installed or restarted; this process is not a reboot-persistent installation.
The source launcher is `tools/start-storefront-tunnel.ps1`. No website listener
was running on 8449 at verification time, so connector health does not establish
website availability.

The current Cloudflare credentials cannot manage DNS. The required proxied CNAME
has not been created:

- Name: `storefront-origin.deckers.gy`
- Target: `7d99e193-e1d1-4449-b4f0-19d5e23db6e2.cfargotunnel.com`

The new **Status → Website Status** screen reads authenticated server status and
refreshes every ten seconds. Viewing requires VIEW_SALES; changing online ordering
requires COMPANY_PREFERENCES and the active primary, enforced on the server. The
control preserves other storefront settings and does not shut down the tunnel or
hide browsing. Disconnected backups learn changes only after synchronization.

Follow-up verification: full Maven suite passed, explicit disposable-database
integration test passed with zero skips, Bash security check and whitespace gate
passed. Installed Swing behavior and public access remain unverified. The running
SmartStock installation and production database were not updated.

## Remaining acceptance

The source gaps listed in storefront-readiness.md remain, including generation/
lease validation, replacement recovery and bounded synchronization at realistic
volumes. Real Auth/email, installed service restart, actual Worker-to-server
failover and recovery, and isolated installed upgrade remain unverified. The
packaging run did not execute the installer, update SmartStock, restart services,
publish R2 artifacts/release metadata, or change Cloudflare resources/DNS. The
later tunnel changes are recorded separately above.

After acceptance: rebuild with a new version, verify the exact artifact's remote
download/size/hash before publishing release metadata, let the user apply the
in-app update, then configure and verify origins before activating deckers.gy.
