# Manager-controlled mobile login persistence

Device Management → Mobile Management now exposes **Keep employees signed in on
this device** below the selected device's name. It is off by default. Review
changes/save persists the policy together with the existing revision-checked
device edits. Device Management permission is required on the server.

The login-page checkbox is removed. Login and MFA do not accept an employee
persistence choice; the server resolves the approved device's current policy
when creating the employee session. Later session requests recheck that policy.
Changing it revokes only that managed device's employee workspace sessions,
including its linked browser credentials. Other devices are unaffected. Existing
clients that omit the field preserve the saved policy.

Off uses the existing temporary-session close/reload logout and idle/absolute
expiry fallback. On uses the existing persistent cookie and rolling inactivity
limit. Force-close/crash cannot guarantee an immediate logout event. Device and
employee permissions, MFA and revocation are still enforced.

The local baseline and migration add remember_login to mobile_managed_devices,
default false. The guarded upgrade is wired into normal local readiness and LAN
schema setup, including the earlier missing employee-session upgrade hook.
Neither policy nor credentials are mirrored to registers/cloud.

## Verification

Real disposable PostgreSQL tests passed for manager-only updates, defaults,
device isolation, existing-session revocation, old-client field omission,
revision conflicts, safe migration/idempotency and schema metadata. Employee
session tests passed for persistence renewal and rejection after policy change.
The Swing preview test ran with a display and verified control/dirty-state
behavior, capturing normal/minimum layouts. All 43 web tests, security checks and
whitespace checks passed. Live iPhone/installed-app acceptance remains separate.

This implementation is local source only until packaged and installed; no new
device policy has been applied to a live employee phone during this change.
