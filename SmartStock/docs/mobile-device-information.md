# Mobile browser device information

Mobile Management → select device → **Device details** displays the most recent
report from that phone/browser. Reports include browser/OS versions, device
family, model when client hints provide it, screen size in CSS pixels, pixel
ratio, touch points, home-screen/browser mode, language, time zone and update
time. The server records the request's user agent separately from client input.

Chrome/Android can supply a model such as SM-S918B. Safari/iOS generally exposes
only the device family, not an exact iPhone generation. Unavailable values read
“Not provided by browser”; the app does not guess a model from screen dimensions.
The administrator-assigned device name remains separate.

Reports are browser-supplied diagnostics, not verified hardware identity. Serial
numbers, IMEI, MAC addresses, personal files, location and stored credentials are
not collected. These reports never authorize a device, change its apps or alter
its login policy. They stay in the local store database, outside cloud mirrors.

Collection runs after successful unified employee sign-in, at most once every
five minutes in the page, with a server-side five-minute write limit per managed
device. Optional client hints time out after one second; denied/missing APIs
fall back safely. Reporting failures do not block the workspace. The endpoint
retains device approval, employee-session, same-origin and CSRF enforcement and
derives the destination device from the paired browser credential. No device ID
is accepted from the browser. Administrator-only state exposes reports to Mobile
Management; employee workspace state does not expose other devices' reports.

Local baseline and guarded existing-store migrations add device_info JSONB and
device_info_updated_at. Both local readiness and LAN provisioning call the new
upgrade. Browser-only metadata is deliberately excluded from authorization,
schema sync and register-facing POS state.

## Validation

Collector tests cover iPhone fallback, Android model hints, desktop-mode iPad and
denied hints. Server tests cover allowed fields, bounds, forged identity/policy
claims, pairing isolation, report rate limiting and administrator-only access.
Disposable PostgreSQL migration tests cover defaults, idempotency and metadata
updates. UI tests verify model and unavailable-field display; the Swing preview
renders the Device details control. A local Chromium acceptance test verified
that the real workspace submits the bounded report with CSRF after sign-in and
without page errors. All 46 selected web tests passed.

This feature is implemented locally; no release, installation, live phone report
or production database change is performed as part of this change. Real iPhone
and installed Android/browser acceptance remain separate checks.
