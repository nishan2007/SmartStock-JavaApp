# Deckers Account — 1.1.134

Implements the selected combined mockup: centered employee photo and name above
grouped nickname, email and phone, followed by password security and account
actions. Header photo falls back to initials. Photo/contact fields are read-only;
only nickname and password have self-service mutations. Profile queries exclude
pay and other personnel fields. See [account behavior](deckers-account.md).

Prepared from frozen 1.1.133 with only the account implementation and tests
overlaid. Unrelated working-tree changes, including the separate remembered-login
work, were preserved but not included. No schema or gateway deployment is needed.

## Validation

- Frozen-source full Maven suite: 1,582 tests, zero failures/errors, 201 skips.
- Final package rebuild after final code/style changes: 25 focused tests, zero
  failures/errors, 9 skips. This includes real disposable PostgreSQL profile/photo
  tests and disposable HTTP Auth protocol tests; no hosted password was changed.
- Frozen-source selected web suite: 40 tests passed.
- Repository security check and changed-file whitespace checks passed.
- Browser synthetic-data acceptance and visual comparison passed at 390 × 844;
  responsive scrolling checked at 320 × 568 and 430 × 932. Evidence and comparison
  history are in the repository-root `design-qa.md` and `output/deckers-account`.
- Final package JAR equals the tested JAR; final profile services and HTML/JS/CSS
  match final source/classes byte-for-byte. Model-free ZIP and hosted model
  catalogue verification passed.

## Publication verified

Windows 1.1.134 / build 101134 was uploaded, downloaded in full, and verified by
size and SHA-256 before release metadata publication. Published metadata was
independently verified against the exact artifact.

- ZIP bytes: 169735106
- ZIP SHA-256: ea56ec6d281d05336565ea7b06df7c33621748831e0951d0286dead432f7ac9d
- JAR SHA-256: ac57ba3d49580e3e2a94143c341795436ee7872957de7e1c9cac35e435e08976

Release evidence is retained outside Git at `D:/SmartStock-release-builds/1.1.134`.
No application was installed, no running store/service was restarted, and no
first-install package or model was published. Install this update on the store
server to serve the new Account screen through deckersapps.deckers.gy. Actual
iPhone/installed-server and hosted Auth acceptance remain separate user checks;
automated and synthetic browser tests do not prove those live conditions.
