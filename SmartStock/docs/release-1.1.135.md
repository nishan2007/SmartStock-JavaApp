# Consolidated outstanding fixes — 1.1.135

Prepared from the verified 1.1.134 release plus the completed changes in
“Add device-specific login persistence” and “Fix orders not loading”. Review of
recent related chats and exact source comparison confirmed the receipt,
custom-order account-charge and three customer-phone changes already exist in
the release baseline. No new live order correction is part of this update.

## Changes

- Unified Deckers sign-in offers Stay signed in on this device, off by default.
  The choice is bound to sign-in and retained through MFA. Remembered sessions
  use a rolling 400-day inactivity limit; temporary sessions use a browser-session
  cookie, revoke on page close/reload when the browser delivers the close event,
  and otherwise expire after 15 minutes of inactivity, with an eight-hour maximum.
  Force-close/crash does not guarantee immediate logout. App switching is not a
  logout trigger. Device approval, active-employee checks and revocation remain.
- Base local schema and registered migration add remember_device to employee
  web sessions. The installer applies the idempotent migration; no live database
  was altered during packaging.
- Company preference reads preserve valid records around a damaged entry.
  Writes use temporary files and atomic replacement where supported, preventing
  interrupted writes from leaving a partially written settings file. This fixes
  the recurring Orders settings-load failure caused by malformed escapes.
- Retains the 1.1.134 Account screen and all preceding published behavior.

## Release verification

Evidence, frozen source, tests and exact artifact verification are retained under
`D:/SmartStock-release-builds/1.1.135`, outside Git. This is a Windows update-only
release. No application installation, service restart, first-install selection,
gateway deployment or model publication is part of this task. SmartStudio and
the previously deferred SmartDrive update remain separate products/work.

Live server/register, iPhone lifecycle, physical printer and macOS checks remain
separate acceptance steps. Update the store server before registers.

## Validation results

The final full Maven package suite passed: 1,585 tests, zero failures/errors,
197 skips. Five employee-web PostgreSQL tests ran against a disposable local
cluster, including remembered-session renewal and expiry/revocation. The initial
run exposed a stale test reflection call; it was corrected and the entire suite
was rerun successfully. All 43 selected web tests, repository security checks and
whitespace checks passed.

Final JAR/ZIP content verification confirms the managed preference helper,
repository integration, employee login service, schema migration registration,
migration SQL and final Account/login HTML/JS/CSS match final source/classes.
The model-free update and hosted model catalogue checks passed.

- ZIP bytes: 169737839
- ZIP SHA-256: 82e2f3eb194ec3e9d9f40fa7ea4de83d88f20dd6c3fbb647f67f06df3eafcec2
- JAR SHA-256: dc6ad62d637164fda0807189ed9c93162a8a2582d3a862c20fd8ba91c7aa0e2d

Windows 1.1.135/build 101135 was published after downloading the complete R2
object and verifying its size and SHA-256. Published release metadata was
independently checked against the exact artifact. No live application or service
was installed or restarted.
