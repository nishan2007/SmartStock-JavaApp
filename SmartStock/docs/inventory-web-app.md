# SmartStock Inventory

Public address: **https://inventory.deckers.gy**. The dedicated mobile catalogue combines active inventory products, custom items, and custom variants. Search while typing by name, description, SKU, primary/additional barcode, brand, classification and other fields supported by SmartStock's shared search helpers. Barcode values retain leading zeros. Results use server-side type/stock filters and 60-item pages; the count describes loaded results. Variant parents are excluded to avoid double counting. Non-inventory items show Not tracked; variable pricing shows Price on request.

The app uses the Deckers purple/orange palette and the active location's receipt company name, logo, motto, address, phone and email. Its separate SmartStock Inventory manifest and PNG/SVG icons support browser installation and iPhone Add to Home Screen. Camera scanning uses the bundled ZXing decoder with a rear-camera preference, photo fallback, and ordinary typed/keyboard scanner input. No external decoder CDN is needed.

## Access

Remote: open View Inventory → Pair Inventory Device using a device manager account. Start the shared employee gateway if needed, name the browser and generate its single-use activation QR. Scan it on the intended browser, then sign in and complete authenticator verification when required. Approval and revocation are shared with the employee gateway; approval grants no inventory permission. Inventory-only accounts can sign in using `VIEW_INVENTORY`; that permission does not grant employee history, orders, or balance sheet access.

LAN: start Status → Mobile Item Web App, activate the browser using its QR and open `/inventory.html` on the same HTTPS store host and port 8444. The original mobile page has an Inventory link. Its sessions and activation are separate from the remote gateway. Trusted HTTPS and store hostname resolution are required for camera access. The public address also works while on store Wi-Fi when internet is available; the LAN address provides the direct store route.

Every search checks View Inventory permission and membership in the active server's store. The client cannot choose another store. Remote and LAN searches require CSRF protection. Results include sale prices but exclude costs and vendor details. PostgreSQL remains the live authority; no inventory credentials or server secrets reach the browser. Inventory is not cached for offline access. The UI clears private item data on sign-out, authorization failure, and backgrounding, and revalidates on return.

## Deployment

The Cloudflare Worker forwards only authentication, activation and inventory search through the existing employee tunnel to loopback port 8450. The store gateway, tunnel and PostgreSQL must remain online. The new `/employees/inventory-health` route distinguishes an old installed server from an outage; frontend health errors report `SERVER_UPDATE_REQUIRED` until the updated server is installed.

Prepare the exact allowlisted assets, then deploy using the existing authenticated Wrangler installation:

```powershell
node SmartStock/tools/prepare-inventory-web.mjs
wrangler deploy --config SmartStock/cloudflare/smartstock-inventory/wrangler.json
```

The preparation script writes to ignored `SmartStock/target/inventory-web-assets`. The gateway serves only Inventory assets and the barcode decoder/license. A Worker deployment does not update the installed Java application. Install the updated server through the normal approved packaging/deployment workflow; source development does not authorize installing a release in a live store. No database migrations are required.

## Verification

```powershell
mvn -q -f SmartStock/pom.xml test
node --test SmartStock/cloudflare/smartstock-inventory/test/gateway.test.mjs
node SmartStock/tools/test-inventory-web.mjs
git diff --check
```

Browser acceptance uses synthetic data only. Set `SMARTSTOCK_TEST_BROWSER_CHANNEL=msedge` to use installed Edge instead of Playwright's bundled Chromium. Set `NODE_PATH` to the runtime dependency directory when needed. Isolated PostgreSQL tests opt in with `-Dsmartstock.test.inventoryJdbc=jdbc:postgresql://127.0.0.1:55451/postgres`, with user `inventory_test` and no password, on a disposable local cluster only. Those tests replace the disposable public schema and never use the configured store connection.

Before live use, verify the installed server, real company branding and stock, LAN TLS/name resolution, browser pairing/revocation, role changes and store boundaries, remote access over mobile data, camera/photo barcode scans on iPhone and Android, and home-screen installation. Automated checks do not prove physical scanner or deployed-network behavior.
