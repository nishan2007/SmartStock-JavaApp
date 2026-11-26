# Dedicated Deckers workspace address

The maintained employee app entry is https://deckersapps.deckers.gy/.
Its dedicated Cloudflare Worker forwards the existing employee API and shared
workspace assets through the store tunnel. The browser stays on the Deckers
address; individual screens mount in one document with one employee session.

SmartStock 1.1.131 accepts this exact HTTPS origin for employee authentication,
mobile-management requests and direct-LAN probes. New Mobile Management pairing
QR codes use the dedicated entry. Existing device grants, role permissions,
CSRF and LAN proof requirements remain enforced.

The Worker rejects unrelated hostnames, paths, cross-site POSTs and forged
forwarding headers. It retains the backend CSP and secure cookie flags. An
older server returns SERVER_UPDATE_REQUIRED without bypassing its origin check.

Install 1.1.131 on the store server before using the new origin for login.
Publication does not install the update or restart a live store.

The 1.1.131 package carries forward the frozen 1.1.130 source, with this
origin/QR change and its tests. The 1.1.130 artifacts remain untouched.
