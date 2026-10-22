# SmartStock 1.1.63 Windows release

Build 101063, 20 October 2026 (America/Guyana).

The verified update and all-in-one installer are published. Independent cloud
metadata matches the update's exact version, build, object path, size and SHA-256.
The installer was fully downloaded and verified before changing the public
selection at https://downloads.deckers.gy/download/windows .

This cumulative working-tree release adds Overseas Orders, phone quote/cart
entry, customer-phone matching, saved sellers, store-specific tracking,
quantity-based arrivals and collection, receipts, and optional separate overseas
cash drawers and store funds. Existing uncommitted source was preserved.

Site: https://overseasshipping.deckers.gy/
Cloudflare Worker: smartstock-overseas
Deployed version: 8f57b446-ec48-488d-8ce5-3044cf0942d7
The time-clock invitation now offers activation for the overseas domain and
preserves the original one-use fragment token. Time-clock Worker version:
1ca2b584-9b35-4bfa-a167-f68b280413ea.

The existing live store server predates the overseas backend. The site therefore
shows a specific server-update notice, rather than presenting a usable quote
screen backed by missing endpoints. Install 1.1.63 on the store server first,
then update registers and activate the browser for the overseas domain.
Publication did not install, restart, reconfigure or migrate the running store.

Validation: Maven package/tests passed (1174 tests, 96 skipped, zero failures or
errors). The optional overseas database suite had separately passed all 17 tests
against the disposable PostgreSQL cluster. Four gateway boundary tests passed.
The full quote workflow passed at phone and desktop widths, including the
separate overseas-only workspace. Live HTTPS verified the branded phone landing,
server-upgrade detection, foreign-origin denial, payroll-route denial and browser
activation requirement. A synthetic invitation was checked without redeeming it.
Security and whitespace checks passed. Model-free archive and hosted catalogue
checks passed; no weights changed or were published. The exact extracted
installer passed application-JAR identity, model-free contents and isolated
native Windows startup. All 862 recorded release inputs remained unchanged
between packaging and publication.

Update ZIP: smartstock-windows-1.1.63.zip
Size: 167277687 bytes
SHA-256: c6f94a3440636335618ae63835bc5bf10d4a1158aad2357c395c3f2f87ee83cd

All-in-one installer: smartstock-windows-setup-1.1.63.exe
Size: 580338954 bytes
SHA-256: 5c68cc8f9938b5cc00ebe647ccb044530903117b502f8cd5d4c0d6b99af5126a

Migration bridge: smartstock-ai-model-migration.jar
Size: 3318 bytes
SHA-256: 86e53fcd5c5e39fb8d16383591b5bde0e7dab7251bd1909730fca535ccdec096

Evidence is under target/release-windows-1.1.63/: package.log,
verification.log, model-hosting-verification.log, acceptance.log, publish.log,
published-release.json, source-snapshot.json, artifacts.json, site-deploy.log,
timeclock-link-deploy.log and site-phone.png.

Live server migration, service restart, real-phone login/activation and order
creation, installed upgrade/rollback, printer, drawer, NFC and staff cash
handover remain separate store acceptance steps. No macOS artifact was built.
