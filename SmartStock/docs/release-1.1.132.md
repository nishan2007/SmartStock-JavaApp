# Re-pair recovery update — 1.1.132

The recovery sheet offers Scan re-pair QR code immediately below Use existing
iPhone pairing. Device managers select the existing approved phone in Mobile
Management and choose Re-pair QR. Each code expires after ten minutes, is
single-use and reconnects the browser to that same device and its current apps.
The employee must sign in again. Revoked approvals cannot be restored by a QR.

Camera scanning, QR-image selection and pasted repair links all stay inside the
current web app. Camera streams and decoder controls stop on completion, cancel,
backgrounding or page exit. The Account screen also exposes device repair.

Prepared from frozen 1.1.131 with only the re-pair service, client/UI, browser
recovery, gateway route and regression-test changes overlaid. Existing local
single-use handoff storage is reused; no database schema change is introduced.

Publication does not install this update. No live server restart or application
installation is authorized. Real iPhone camera/local-network permission and
installed-app acceptance remain user checks. Browser visual inspection was not
available in this chat; frontend placement and scanner lifecycle were tested
through the JavaScript harness, and server behavior through real HTTPS against
disposable PostgreSQL.

## Published verification

Windows 1.1.132/build 101132 is published and its metadata matches the final
artifact. The stored object was downloaded and verified before metadata creation.

- ZIP bytes: 169726482
- ZIP SHA-256: b24ea8669cde4fd9cd6cb9a8d9498b4169355912183327cb070caaee9d1dd0bb
- Final packaged JAR SHA-256: 341a58f9b96a10e807bd55581ab3fa8133125901ca434b1f9d00eb32a615d9ab
- Full Maven suite: 1571 tests, zero failures/errors, 201 skipped.
- Selected web tests: 39 passed, including recovery placement, QR submission,
  camera denial/cancellation and same-app fallback.
- Real HTTPS/disposable PostgreSQL acceptance: admin-only idempotent issuance,
  same-device redemption, unchanged grants, no duplicate approval, fresh login,
  and expired/reused/revoked-code rejection all passed.
- Exact final recovery resources, service/client classes and administrator action
  were checked inside the JAR, and that JAR matched the update ZIP.
- Model-free archive, hosted catalogue, security and whitespace checks passed.

The initial candidate's publication was stopped before metadata existed when its
recovery resource differed from final source. The standard package was rebuilt
with the final resources; the already-passing Java suite was not repeated for
that resource-only rebuild, and the final JavaScript suite/content hashes were
verified again. The initial unreferenced object is not the published release.

The five legacy staff gateways now accept the repair endpoint and decoder assets;
the dedicated Deckers gateway already proxies these employee workspace routes.
Production variables and service bindings were checked before deployment.

No application was installed or restarted. Install the update on the store server
and the workstation used for Mobile Management before using this feature. Real
iPhone camera/permission/layout acceptance and physical hardware checks remain
pending. No first-install installer or AI model was published.
