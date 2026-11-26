# Mobile Management

Open **Device Management → Mobile Management → Pair phone**, enter a name,
select its initial staff apps, and scan the single-use QR in the phone's browser.
The approval lasts ten minutes before redemption. Bookmark the resulting
Deckers workspace. Employee sign-in and existing MFA still apply; approving a
phone never grants an employee role permission. The workspace lists only apps
allowed by both the device grants and the current employee role. See
[Deckers employee workspace](deckers-workspace.md) for the unified home, login
and store-network checks.

## Existing iPhone home-screen icons

iOS keeps each installed web app's browser storage separate from Safari. Pairing
in Safari cannot write approval cookies into icons that were installed earlier.
The icon's missing approval is a separate browser context, not a new phone.

When an existing icon reports missing device approval, select **Use existing
iPhone pairing**. In Safari where the phone was paired, confirm **Connect this
app**, then return to the home-screen app. If the opened in-app browser does not
recognize the pairing, use **Copy Safari confirmation link** and paste it into
the Safari browser where pairing succeeded. The pending request survives an app
reload while switching browsers. Do this once for each existing isolated icon;
it requires no further administrator QR and creates no additional phone record.

Connections expire after ten minutes. The requesting app holds a separate
HttpOnly claim cookie, and only that app can collect the approved connection.
Confirmation requires an already paired browser and current access to the
requested app. Revocation and app switches still apply to all linked contexts.
Only confirm a request you started on your own phone.

Select a paired phone to change its name or enabled apps. **Review changes**
shows the changes before saving. Changes apply on the next protected request.
**Revoke phone** blocks every staff app and every linked origin. New catalogue
entries are disabled until explicitly enabled. Another browser profile, cleared
site data, or an isolated installed web app may require pairing again.

## Deployment

Ship the Java server and staff-web assets together with the updated Orders,
Inventory, Customer Management, Balance Sheet, Overseas, and Time Clock workers.
The workers expose only the scoped `/employees/mobile-management/` launcher,
assets, and pairing/handoff endpoints. Keep the existing origin and tunnel
configuration. This change does not deploy workers or install into a live store.

The normal local schema/provisioning path installs
`20261119120000_shared_mobile_management.sql`. The same definitions are included
in the local baseline. `20261119130000_mobile_home_screen_links.sql` adds pending
home-screen connections through the same guarded provisioning path. Existing independently approved browsers must pair once
again; legacy browser/session records remain for history and cannot bypass the
shared guard. App restarts may require employee sign-in but retain device approval.

The public pairing launcher uses the existing permanent Time Clock address when
Employee Web is running, and otherwise the Mobile Items LAN address. The LAN
apps still require store Wi-Fi. An app being enabled does not start its service.
Keep the public staff gateways pointed at the same store authority. Customer
storefront sessions are separate.

Approvals, grants, hashed credentials, pairing codes, and handoffs live only in
the store database, outside register reference synchronization and cloud row
mirroring. No cloud schema or register database credential is added. Pairing and
handoff secrets travel in URL fragments; the page removes them before redemption.
Only their hashes are persisted. Cross-origin handoffs last 60 seconds, are
single use, and are checked against their destination and current device grants.

## Adding an app

Add its stable key, name, group and destination to `MobileDeviceService.APPS`,
then map its protected API routes in `routeApps`. Unknown routes fail closed.
Register the scoped launcher handler on a new gateway and allowlist its public
origin if it proxies the Employee Web listener. Keep employee permission checks
in the app's existing authenticated service. No device-table column or UI-specific
checkbox is needed for each new app.

## Verification

`MobileDeviceServiceTest` covers the registry without a database and runs its
database scenarios only against the explicitly configured isolated cluster:
`-Dsmartstock.mobile.test.jdbc=jdbc:postgresql://127.0.0.1:55473/mobile_management_test`
(user `mobile_test`, local disposable cluster). It verifies replay, expiry,
concurrent redemption, persistent approval, role boundaries, mutable grants,
cross-origin handoffs and revocation. Gateway tests are in
`test/web/mobile-management-gateways.test.mjs`.

Before rollout, verify a real phone through each deployed origin, server restart,
the installed Swing screen at normal and minimum sizes, and the production-schema
upgrade in a disposable restored store. Hardware and live installation are
separate checks.
