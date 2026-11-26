# Edit Items web app

Start Status → Mobile Item Web App on the store server. Approve a browser by scanning its one-time activation QR, sign in with employee credentials, and follow Edit Items. The dedicated address is /edit-items.html on the same HTTPS store host (port 8444). Bookmark it or use the browser's Add to Home Screen / Install option; its manifest has a separate Edit Items identity and icon. Installation support varies by browser.

Both the store LAN and browser activation are required. Existing server-side employee permissions, CSRF protection, audit and idempotent save handling apply. No store ID or database credentials are entered on the phone: the authenticated server supplies the location ID. This does not discover a server on arbitrary Wi-Fi; open the store QR/link first. The app does not save offline.

Choose Inventory or Custom, search by name/SKU/barcode, or scan with the rear camera. A keyboard barcode scanner also works in the search box. Open a result, edit its details or primary barcode, and enter additional barcodes one per line or scan them. Save changes applies the existing server validation and duplicate-barcode checks. Custom variants can be edited from search results.

Verify on the installed store server: TLS trust and name resolution, activation/revocation, employee permissions, store ID, primary/additional barcode lookup and conflicts, camera and photo fallback on iPhone/Android, home-screen icon, and save/reload against a test database before live use. No deployment or live-data mutation is part of this source change.
