# Deckers employee account

In the unified app at `https://deckersapps.deckers.gy/`, Account displays a centered
employee photo, full name, nickname, email and phone. Photos and contact details
come from the local employee record and cannot be edited through this screen.
The header uses the same photo. Missing or failed images fall back to initials.
The server produces a 320-pixel JPEG thumbnail; private image references and
storage credentials are never returned to the browser.

Employees can set, edit or remove their nickname. The endpoint accepts only a
nickname, takes the employee ID from the authenticated session, and records an
audit event. It accepts up to 100 characters and rejects control characters.
No pay, banking, identity documents or other personnel fields are selected or
included in the profile response. No new database schema is required.

Change password asks for the current password, new password, confirmation, and
an authenticator code when MFA is enrolled. Online Auth verifies the current
password and identity, and an enrolled authenticator is verified before the
authenticated password update. Mandatory MFA cannot be bypassed after removal
of a factor. The browser never receives Auth access/refresh tokens or server keys.
Password whitespace is preserved, and hosted password policy still applies.
After a successful update, the local password cache is invalidated and other
employee workspace sessions are revoked. The current workspace remains signed in.

All account requests retain approved-device, active-employee, same-origin,
session and CSRF enforcement. Password attempts use the existing rate limiter.
Dialog close/sign-out clears password fields; sign-out also clears profile data.

Device and app access expands below the account controls and retains Refresh
available apps and Repair device approval. Device grants and employee app
permissions remain managed through existing administration.

Legacy LAN and Scheduler gateways keep their separate native sessions. They
do not send their session's CSRF token to Employee Auth; self-service editing is
available through the unified Deckers address.

## Verification

Unit and disposable HTTP tests cover password whitespace, identity matching,
current-password Auth, MFA verification/omission, mandatory MFA and hosted policy
rejection. Disposable PostgreSQL tests cover the safe-field projection, photo
thumbnail generation, nickname removal, isolation between employees, and inactive
employee rejection. JavaScript tests cover avatar fallback, private-data cleanup,
nickname saving, password confirmation, CSRF and legacy-session separation.

Browser acceptance uses fictional data and a synthetic Auth stub. It verifies
layout and controls, not the store's hosted Auth configuration. Live iPhone,
installed-server and real hosted password acceptance remain separate checks;
no real employee password was changed during development.
