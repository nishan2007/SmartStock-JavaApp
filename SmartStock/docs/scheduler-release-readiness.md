# Scheduler release readiness — 2026-08-29

Status: **NOT approved for production deployment.** No release was published and
the installed production application/database was not updated during this audit.

## Revised sign-in decision

The owner subsequently requested password-only scheduler login until a later MFA
rollout. MFA is now opt-in with `SMARTSTOCK_SCHEDULER_REQUIRE_MFA=true`; existing
production authenticators are not removed. The live acceptance gateway uses the
explicit single-owner production-Auth bridge with development schedule data.
No production password or local user identity was copied or rewritten. Tests for
bridge isolation and password-only assurance have been added. The full suite now
reports 472 tests, zero failures/errors, 11 skipped; security checks passed.
The archive hashes below describe the earlier build and must NOT be used to
publish these newer authentication changes. Rebuild before release.

## Verified

- Full Maven run: 468 tests reported, 0 failures, 0 errors, 11 skipped.
  Skipped integration tests are not evidence of live database behavior.
- Repository security check passed using Git Bash outside the Windows sandbox.
- JavaScript syntax check and `git diff --check` passed.
- Windows packaging completed for the installer and update ZIP.
- Rebuilt ZIP size: 53,070,452 bytes. SHA-256:
  `a9aa39854bd076e92a6618cff681c5a295871436154d5db30c90d4c79613c8a7`.
- Rebuilt installer SHA-256:
  `02e497f6ea966fc79cf80549565d79e4468de71fc401ff5a197e49a8a6ce6ed1`.
- Independently read the Cloudflare executable from inside the ZIP and verified
  its pinned SHA-256. The executable and license are beneath `dependency/`, so
  they travel with both desktop and background-service updates.
- Updater regression test covers nested Cloudflare dependency copying.
- Added behavioral validation tests for password preservation, HTTPS origins,
  clear-period confirmation, bounded periods, QR encoding, and binary tampering.
- Added behavioral tests for AAL2-only session creation, CSRF hash matching,
  strict idle/absolute expiry, and sanitized database/conflict errors.
- Restored the retained production snapshot into a separate password-protected
  PostgreSQL 17 cluster listening only on loopback port 55439. All 146 table
  row counts matched the snapshot inventory; backup SHA-256 was unchanged.
- The application's schema-contract upgrade passed against the restored
  production snapshot. Scheduler migration rollback/replay preserved 144
  checked business tables (permission and scheduler security tables excluded).
- Real PostgreSQL tests verified revision changes/rollback and blocking of a
  concurrent desktop-style write during the browser compare-and-write lock.
  The isolated PostgreSQL cluster was shut down after testing. Neither the
  live production database nor the retained backup was changed.

## Repairs made

- Installed background service starts/stops the scheduler controller.
- Asynchronous tunnel startup, single-controller database lease, generation
  restart detection, retry delay, and interrupted-start child-process cleanup.
- Background-service copy failures trigger application rollback instead of
  being reported as a successful update with mismatched service files.
- Correct authenticated Supabase user/factor lookup; AAL2 session gate,
  local access check before enrollment, exact password preservation and SVG QR
  conversion. Login attempts are reserved before outbound authentication.
- Bounded executor queue and expiring/bounded pre-auth/proposal memory.
- Runtime/role checks, transaction-local mutation permission/session checks,
  and atomic scheduler switch/session revocation.
- Server-confirmed clearing, bounded date ranges, local-calendar dates,
  duplicate-save prevention and retry idempotency-key retention in the browser.
- Deployment instructions now require backup before migration and distinguish
  binary rollback from database recovery.
- Browser mutations now require the displayed period's revision, including
  assignment, holiday and shift changes. Comparison and writing share a short,
  bounded database lock that also excludes desktop writes. Dialogs retain
  their original revision rather than silently adopting a newer snapshot.
- Logout now checks CSRF and records an audit event. Invalid database values,
  conflicts and unavailable-server responses are sanitized.
- Shared scheduling schema checks are completed before opening the web
  listener, not first invoked inside a browser mutation transaction.

## Blocking checks / remaining work

1. The original development-account restore test could not create a database.
   This was resolved for acceptance by restoring into a new isolated cluster;
   no broader privileges were granted to the SmartStock service account.
   Recovery is now verified at the SQL archive/schema/row-count level; a full
   recovered application startup and business workflow test still remains.
2. Complete server authentication/session integration coverage (including
   HTTP-level logout/audit behavior, owner-role policy, revocation and expiry), and
   live TOTP enrollment/login. Existing source-level assertions are insufficient.
3. Ordinary-edit revision checking and desktop-write exclusion are implemented
   and database-tested. Complete multi-browser end-to-end conflict handling and
   Auto Schedule conflict tests; verify desktop/web scheduling contracts.
4. Schema-contract upgrade from the production snapshot passed. Concurrent
   application startup and forced upgrade-failure recovery still need coverage.
5. Test the exact installer/updater in an isolated installed Windows instance:
   service stop/start, locked files, rollback, launcher paths, tunnel restart,
   status link, and denial of non-scheduler public routes.
6. Perform phone/browser tests with real MFA: view-only/edit/other-store roles,
   create/edit/remove, holiday/shift management, Auto Schedule, disconnect and
   session recovery. No live save or cellular acceptance has been verified here.
7. The retained production backup has now been restore-tested. Take a fresh
   snapshot immediately before deployment if new store transactions have arrived;
   this backup does not include changes made after its snapshot time. Never
   overwrite live data to test recovery.

The build is a local candidate, not a published update. These hashes must be
recomputed if any packaged source changes. Public download/size/hash verification
is still required before publishing release metadata.
