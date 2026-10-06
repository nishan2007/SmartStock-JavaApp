# SmartStock Windows update 1.0.235

Prepared 2026-10-01, build 100235. SmartStock update ZIP only; the separate
SmartStudio installer is version 0.1.1.

## Changes

- Device Management adds Allow Studio alongside Allow Sales and Allow Orders,
  with Studio status visible in the existing device list.
- Studio enrollment uses the computer's exact fingerprint, hostname, and store
  assignment to reuse its physical-device row. New physical computers create a
  row. Studio keys and hashed credentials live in a separate LAN-local table.
- An old 0.1.0 Studio duplicate is retired and hidden after re-pairing, while its
  stored row and references are retained for history. The register key is preserved.
- Allow Studio is enforced on every Studio credential request. The credential
  cannot invoke register, cloud-storage, or administrative endpoints.
- Session application scope prevents Studio login/logout from revoking or ending
  register sessions on the same device. Older device-management clients preserve
  the Studio flag when saving unrelated settings.
- Base local/cloud schemas, the ordered migration chains, server provisioning
  contract, and credential/session/identity query indexes include the new fields.
  Studio credential rows are not part of reference sync or the cloud data manifest.

## Verification

- Windows packaging completed Maven package/tests: 774 tests, zero failures/errors,
  37 environment-dependent skips.
- Five isolated PostgreSQL enrollment/session tests passed with no skips, including
  device-row reuse, retired duplicate handling, new-device registration, register
  key preservation, server-side allow/deny enforcement, and session independence.
- The exact final ZIP ran offline image inference with the installed Java runtime.
  PNG alpha, original dimensions, and bounded input were verified; its JAR matched
  the tested build.
- An isolated database provisioned from the previous published 1.0.234 ZIP upgraded
  and passed the schema readiness checks using the exact final 1.0.235 ZIP.
- Repository security checks and `git diff --check` passed.

No production server/database/service was upgraded by these checks. Live staff
pairing and the user's specific network-share image require acceptance after
installation. Printer, cash drawer, and NFC checks were not performed.

## Artifact

- ZIP: `target/release-windows-1.0.235/smartstock-windows-1.0.235.zip`
- Size: 296,631,161 bytes
- SHA-256: `840fb0465813336f7ba49acec3eccb6a2f09f436a2235c5df400b26ffb8332b6`

## Upgrade steps

Update the store server to 1.0.235. Install SmartStudio 0.1.1 and enter a fresh
pairing phrase once. Enable Allow Studio on the existing computer row and check
approval in SmartStudio. Staff use their existing account to sign in.

## Publication

Published as Windows build 100235. The R2 ZIP was downloaded and its size and
SHA-256 matched before release metadata was written. An independent production
feed read confirmed 1.0.235 is the latest Windows update with the expected hash
and size. No SmartStock installer or installer portal change was published.
