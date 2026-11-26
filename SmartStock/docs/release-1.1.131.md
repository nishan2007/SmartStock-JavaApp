# Deckers dedicated address — 1.1.131

The Deckers employee workspace uses https://deckersapps.deckers.gy/.
The dedicated gateway is configured. SmartStock 1.1.131 accepts that exact origin
for authentication, screen requests and local-network probes, and generates new
device-pairing QR links for it. All screens retain the shared login and in-app
navigation, with device grants and employee permissions enforced by the server.

This update carries forward the frozen 1.1.130 release source. Its only added
application changes are the dedicated origin and QR entry, plus regression tests.
The 1.1.130 source and artifacts remain intact.

The Windows updater is prepared outside Git under
`D:/SmartStock-release-builds/1.1.131`. No installation into the live store is
authorized. Install the published update on the store server before using the
new address for login. Until then the dedicated gateway explicitly reports that
the server needs updating.

The dedicated gateway rejects foreign origins and unrelated paths, preserves
secure session cookies and CSP, and keeps the backend origin checks intact.
Gateway version: `ffeb4891-8067-4735-9ff1-991909cd5c65`.

Installed-app, real iPhone, camera, local-network permission, printer, cash drawer,
NFC and live database checks remain separate user acceptance. Installation tests
are not performed for this update, following the user's instruction.

## Published verification

Windows 1.1.131/build 101131 is published to the existing update feed.
The artifact was uploaded, downloaded in full, and verified before release
metadata was created. The published metadata was then checked against the exact
local artifact size, checksum, version, build and object path.

- ZIP bytes: 169722837
- ZIP SHA-256: 9c393bcdf19a3c7c12cd2d0d47172a22e4e09b93eec163f4f3afd28e39a00bab
- Tested/packaged JAR SHA-256: 4ab34676497c72e045319ff13324e5ac431efbbc65a14fe258311eba68239c6d
- Maven: 1570 tests, zero failures/errors, 200 skipped.
- Selected web tests: 35 passed, including dedicated-domain routing and origin
  protection. Real HTTPS/disposable-database acceptance checked the dedicated
  origin on workspace state and employee API calls, including rejection of a
  lookalike origin.
- Security check and whitespace check passed.
- Model-free archive and hosted model catalogue checks passed.
- The exact tested JAR and canonical-domain classes were verified inside the ZIP.
- Live dedicated HTTPS entry and rejection of foreign-origin POSTs passed.

No installed application was updated, no server service was restarted, and no
first-install package or AI model was published by this work. Installed-app,
real-phone, local-network/camera and physical hardware acceptance remain pending,
as installation was explicitly excluded by the user.

The app must be updated on the store server before login from the new domain is
enabled. The dedicated gateway reports SERVER_UPDATE_REQUIRED until that update.
