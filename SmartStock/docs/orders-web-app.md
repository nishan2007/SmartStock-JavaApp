# Orders web workspace

Public address: **https://ordersmanagement.deckers.gy**. The workspace works in
iPhone Safari over Wi-Fi or mobile data. Safari's Share > Add to Home Screen adds
its standalone shortcut with the Orders name and Deckers icon.

The Cloudflare Worker serves only Orders assets and forwards allowlisted employee
authentication and Orders requests through the existing store tunnel. The gateway
stays bound to loopback; PostgreSQL remains the local store authority. The active
store server, employee gateway, and tunnel must be online. Order data is not cached
for offline use.

## Pair a browser

1. On the store server, open **Orders Dashboard > Pair Orders Device** using an
   account with the existing device-management permission.
2. Start the shared employee gateway if needed and wait until it is running.
3. Enter a browser name, select **Generate Activation QR**, and scan it on the
   intended iPhone/browser. The QR uses the public Orders URL, expires after ten
   minutes, and can be redeemed once. Its token stays in the URL fragment until
   redeemed; it is not sent in asset URLs or referrers.
4. Sign in with the employee account and complete authenticator verification if
   required by the existing employee gateway policy.

Browser approval and revocation are shared with the employee gateway. Approval
alone grants no employee permissions. Revoking a browser or stopping that gateway
prevents subsequent Orders requests. The LAN picture-app QR cannot authorize this
public site; pair again using the new Orders flow.

## Permissions and operations

- Remote sign-in requires the existing `ACCESS_EMPLOYEE_WEB` permission.
- Queue, delivered orders, and details require `ORDERS_MANAGER_DASHBOARD` or
  `MANAGE_CUSTOM_ORDERS`.
- Assignment and status changes additionally require `MANAGE_CUSTOM_ORDERS` or
  `CUSTOM_ORDER_OVERRIDES`, enforced by the server on every request.
- Employees must be assigned to the active server's store. Client-supplied store
  identifiers cannot select a different store. Overseas orders are excluded.
- Assignments reuse the desktop domain service, transactions, validation, and
  audit history. All authenticated remote reads and writes require a CSRF token.
  The gateway checks the origin, browser approval, active user, permissions, and
  session expiry. No server credentials are sent to the iPhone.

The workspace supports active-order search and status filters, summary metrics,
seven days of delivered orders, details, and assignment/status updates. Creation,
pricing, payments, line production/delivery, returns, and cancellation continue
through the existing desktop workflows.

## Deployment and verification

`cloudflare/smartstock-orders/wrangler.json` binds the public custom domain.
Deploy with the repository's installed Wrangler using that config. Assets have
an explicit allowlist.

Install the updated SmartStock server through the normal approved deployment
workflow. Publishing this Worker does not update the installed Java server.
`/employees/health` reports `SERVER_UPDATE_REQUIRED` until the store gateway
supports Orders, or `STORE_UNAVAILABLE` when it is offline. The frontend shows
that state before accepting a sign-in.

From the repository root, run:

- `node --test SmartStock/test/web/orders.test.cjs`
- `node --test SmartStock/cloudflare/smartstock-orders/test/gateway.test.mjs`
- The normal Maven, security, and whitespace gates.

Live verification still requires a separate iPhone: scan the new QR, switch to
mobile data, sign in, open an order, save an assignment, inspect the audit record,
and confirm revocation denies further requests. Also check Add to Home Screen.

## Printed order label scanning

Use **Scan label** next to search to scan the Code 128 barcode printed on custom
order labels and slips. A matching active order opens immediately. If the order
is not in the loaded queue, the authenticated `/orders/lookup` endpoint performs
an exact lookup at the employee's assigned store, including historical delivered
orders. Delivered and cancelled orders remain read-only in the assignment form.
Typing an order number and pressing Enter follows the same lookup, including
keyboard-style barcode scanners.

The scanner uses the rear camera, with a take/choose-photo fallback. Decoding
runs entirely on the device; camera frames and photos are not uploaded. Camera
tracks and decoding stop when the scanner closes, the page becomes hidden, or
the employee signs out. Safari camera permission is required for live scanning.
Physical iPhone camera and printed-label verification remain separate checks.

The vendored `orders-barcode.js` is the npm `@zxing/browser@0.1.5` UMD bundle.
Its package SHA-512 integrity was verified before extraction. Bundle SHA-256:
`b5ad3df920738ca7adcb74508d7e6b6a5b9024993fb9a0c702da1ad8964eca07`.
MIT and bundled ZXing/text-encoding licenses accompany it in
`orders-barcode-LICENSE.txt`. No external CDN is needed at runtime.
The decoder test fixture was generated using SmartStock's
`ReceiptBarcodeRenderer.renderCode128("CO-1042", 552, 112)` with sample data.

## SmartStock branding

Orders uses the shared `DeckersPalette` purple (#7022a8), orange (#ff5b00), lime
status accent, and light surfaces. Signed-in company details come from
`ServerCompanyCustomizationRepository.loadReceiptSettingsForLocation`, including
the saved company name/logo, motto, and location contact/address lines. No separate
company profile is maintained by this web app. Only bounded PNG/JPEG logos are
returned inline through the authenticated dashboard response. Before sign-in or
when settings are unavailable, the UI uses the SmartStock name instead of invented
company information. The installed Java server must include this branding update.
