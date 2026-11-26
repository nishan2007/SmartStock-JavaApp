# Deckers employee workspace

The shared mobile workspace provides the plum, lavender and warm-white Deckers
home, employee login, MFA, app search, account controls and a standalone web-app
manifest. The permanent Time Clock root opens this home. Existing staff-app
addresses remain valid. The Add Item and Receiving Inventory tiles belong to the
same grid as every other authorized app.

## Approval and authorization

Device Management → Mobile Management remains the single approval authority.
The existing device, app-grant, credential and single-use handoff records are
reused. No new database schema, device approval, register credential, or cloud
store database is introduced.

The workspace requires an employee session before listing apps. Both listing and
opening an app check its current device grant and current employee permissions.
An administrator enabling an app for a phone does not grant that employee a role
permission. Existing app APIs retain their authorization and MFA requirements.
Changing grants or revoking a phone applies to subsequent protected requests;
returning to the workspace refreshes its available apps.

The public Deckers workspace uses one employee session for all 15 screens.
`DeckersScreens` supplies the existing templates and controllers as mounted
screens inside the same document, with scoped DOM/CSS and a persistent Deckers
header, Home action and Account navigation. There are no child iframes and no
page navigation when opening a tile. Screen exit removes listeners, timers,
observers and camera streams. Existing unsaved-change guards run before leaving.

Screen requests use the same origin and employee cookie. The server validates
current employee permissions, current device grants, session expiry and CSRF on
every screen operation, then dispatches through the existing workflow services.
Mobile operations use the parent employee session identity without a second
mobile login. Scheduler keeps an internal, non-bearer bookkeeping row for its
existing idempotency table; no second browser token or scheduler login is issued.
The common login honors either configured employee or scheduler MFA requirement.
Independent legacy app addresses and authentication remain available until phone
acceptance is complete. Order Design includes an order-number chooser.

Installed iPhone web apps can use **Use existing iPhone pairing** to connect the
Deckers workspace to the existing approved device. The `WORKSPACE` link purpose
does not become an app grant and cannot authorize an app API. It only links the
isolated browser storage to the same device record. Employee sign-in still applies.

## Store network check

All existing LAN-only workflows, including Add Item and Receiving Inventory,
remain selectable in the grid. Tapping one obtains an authenticated, short-lived
challenge bound to the employee, device, app and source origin. The browser then
contacts the store's HTTPS Mobile Items UI listener directly, without cookies.
Only a non-forwarded local peer on that listener can verify the challenge.
Public staff gateways do not expose the verification endpoint.

Opening a stock screen and every subsequent stock operation require a fresh
verified proof, consumed once. Device and employee authorization are rechecked
before the existing local workflow runs. Proofs expire after 30 seconds, are memory-only, and are bounded in number.
They do not queue inventory changes or contain pairing/session credentials.

If the listener cannot be reached, the browser shows **Connect to the store
network** with a **Got it** button. It does not navigate to a broken LAN URL or
claim a stock change succeeded. Camera, certificate trust, browser local-network
permissions, and the listener's installed state remain real-phone verification
steps. The original standalone stock APIs retain their LAN boundary. The shared screen
API requires the verified direct-LAN proof for every stock request.

## Delivery and verification

Ship the Java services and shared-mobile resources together with the updated
Time Clock, Orders, Inventory, Customer Accounts, Balance Sheet and Overseas
workers. Existing native app resources and services are retained. This work does
not remove the individual apps. Deployment receipts are recorded in the release notes.

`DeckersScreensTest` checks all screen controllers and app-operation boundaries.
`DeckersWorkspaceTest` tests the device/role intersection and proof binding/replay.
`MobileDeviceServiceTest` exercises approval, mutable grants, revocation and
installed-workspace linking on the explicitly isolated mobile test database.
`PhoneMinutesIntegrationTest` exercises real HTTPS employee handlers and LAN
preflight/proof verification against a disposable local database and TLS profile.
It also opens all 14 screen descriptors and exercises stock and Scheduler through
one parent employee session, missing CSRF, cross-app denial and proof replay.
It also changes app grants through the actual Mobile Management HTTPS API,
observes workspace visibility, completes a verified Receiving launch, rejects
proof replay, checks employee-role revocation and revokes the phone through that
same administration API. No second device approval is created.
Gateway tests cover assets, checked origins, cookies and blocked public probes.

`node SmartStock/tools/preview-deckers-workspace.mjs 4173` starts a loopback-only UI
fixture with production HTML, CSS, JavaScript, icons and fictitious employee data.
It never connects to a store database or Supabase. Browser scenarios use
`?scenario=login`, `mfa`, `unapproved`, `limited` or `empty`. Its app destination
and authentication responses are fixtures, not a production store deployment.

Visual evidence and the selected reference are in `output/deckers-workspace/` at
the repository root. The existing project-level design QA files are preserved;
the feature report is `docs/deckers-workspace-design-qa.md`.

## Brand assets

The header uses the existing `Images/Deckers.png` unchanged. Orders and Inventory
reuse their existing web-app PNGs. The remaining charcoal tiles use the existing
bundled Font Awesome font and retain its license. No new external font or icon
request is required.

The home-screen icons are saved in `src/shared-mobile/deckers-180.png`,
`deckers-192.png`, and `deckers-512.png`. They were prepared with built-in image
generation from the existing Deckers logo, then resized for the manifest. Prompt:
“Create a production web app home-screen icon PNG, square 1024x1024, completely
flat uniform dark charcoal #25272b background. Place the exact attached existing
Deckers multicolor wordmark centered, unchanged in lettering spelling shape and
color, preserve its original wide aspect ratio and include the whole logo.
Wordmark should occupy 84 percent of square width with generous charcoal top and
bottom padding. No other symbols letters labels borders gradients shadows bevels
rounded corners phone frame or UI. This is asset preparation using an existing
brand logo, not a logo redesign.”

## Per-device sign-in persistence

Login persistence is controlled by administrators in **Device Management → Mobile Management**. Select a phone, toggle **Keep employees signed in on this device**, then review/save the change. The default is off. The employee login has no persistence checkbox and employee-supplied preferences are ignored.

The server reads the approved device policy when sign-in completes, including after MFA. A policy change ends that device's existing workspace sessions, requiring a fresh login. Other devices keep their own policy and sessions. Renaming/editing apps from an older client preserves the existing login policy.

Off uses a Secure HttpOnly browser-session cookie and an authenticated keepalive logout on page exit/reload. App switching does not log out. Crash, force-stop or network loss can prevent the exit request; 15-minute idle and eight-hour maximum limits remain the fallback. On uses a persistent Secure HttpOnly cookie and rolling 400-day inactivity limit. Device revocation, account disablement and employee permissions still apply on requests.

The device-policy migration is included in the local baseline and both existing-store upgrade paths. No browser/Auth tokens are copied to registers or cloud mirrors. Real iPhone close/reopen and force-stop behavior remain separate installed-device acceptance checks.
