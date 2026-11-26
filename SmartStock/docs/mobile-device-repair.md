# Re-pairing a Deckers browser

The recovery sheet offers **Scan re-pair QR code** immediately below
**Use existing iPhone pairing**. These are separate options. The Account screen
also offers **Repair device approval** for a browser that can still sign in.

In Device Management → Mobile Management, select the existing approved phone and
choose **Re-pair QR**. Save or discard pending device edits first. The QR is valid
for ten minutes and one redemption. Issuing another QR invalidates earlier repair
codes for that device. No new app permissions are selected during repair.

On that phone, use **Scan re-pair QR code** to scan directly inside the installed
web app. A QR image and pasted repair link are available when camera access is
unavailable. Only repair links for the dedicated Deckers address are accepted.
The phone reconnects to the same managed-device ID and current enabled apps.
It must sign in again; the previous browser credential and login do not transfer.
Other browsers linked to the managed device are retained.

Only device managers may issue repair codes. Revoked devices cannot issue or
redeem them, and expired or consumed codes fail. Employee role checks, device app
grants and local-store network requirements remain mandatory. Repair reuses the
existing local single-use handoff storage and introduces no schema changes.

Automated verification covers separate button placement, same-app QR submission,
camera cleanup, rejection of unrelated links, idempotent admin issuance, same-ID
redemption, unchanged app grants, duplicate-device prevention, expired/reused/
revoked codes, administrator enforcement and fresh employee authentication.
Real iPhone camera, browser permissions and installed-app acceptance remain
separate user checks. This update is packaged/published without live installation.
