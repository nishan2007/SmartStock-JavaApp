# SmartStock Windows update 1.0.234

Prepared on 2026-10-01, build 100234. Update-only release for SmartStudio support;
no full installer or installer portal publication is included.

## Behavior

Adds paired HTTPS LAN routes for SmartStudio company branding and staff-authorized
offline background removal. Branding is bound to an approved device's assigned
store; remote administrators require an authenticated session. Removal always
requires device and employee authentication plus an existing item/custom-order
permission checked on the server. Browser and desktop clients share one worker,
PNG/JPEG validation, a 6 MB upload limit, and a 12-megapixel dimension limit.

Companion enrollment uses a separate installation preference node, preserving
the SmartStock register's identity on the same computer. The portable SmartStudio
client retains separate credential files and uses existing staff login APIs.

## Artifact

- ZIP: `target/release-windows-1.0.234/smartstock-windows-1.0.234.zip`
- Size: 296,618,424 bytes
- SHA-256: `45d7e8ce8958ed8fb227d64d864e43d62cf06d11f732eda0df7bc526df1d015e`

## Verification

- Existing Windows packaging script ran Maven package/tests: 769 tests, zero
  failures/errors, 33 environment-dependent skips.
- Repository security checks and `git diff --check` passed.
- Exact final ZIP extracted; its application JAR hash matched the tested build.
- A probe compiled against that ZIP and run using the installed SmartStock Java
  runtime verified the native routes, separate enrollment identity, bounded input,
  offline inference using the packaged model, PNG alpha, and original dimensions.
- SmartStudio's portable launcher and login rendering were verified separately.

The installed production application was not upgraded as part of publication.
Live pairing, staff login, company branding, and end-to-end desktop removal need
verification after applying this update to the store server. No database, service,
printer, drawer, or NFC live acceptance check is claimed by these build checks.

## Publication

Published successfully as Windows build 100234. The uploaded R2 object was
downloaded and its size and SHA-256 matched before release metadata was written.
No installer was created or selected, and the installer portal was not changed.
An independent production feed read confirmed 1.0.234 / build 100234 is the
latest published Windows update, with the expected size, object path, and hash.
