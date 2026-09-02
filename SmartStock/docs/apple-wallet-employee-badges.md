# Apple Wallet employee badges

SmartStock can issue a signed Apple Wallet employee badge from Employee Management. The initial release uses the pass QR barcode at register login. The normal company preference controls whether the employee must also enter a PIN.

Configure these values only on the store server through protected machine-local environment variables or service configuration:

- `SMARTSTOCK_WALLET_PASS_TYPE`
- `SMARTSTOCK_WALLET_TEAM_ID`
- `SMARTSTOCK_WALLET_SIGNING_P12`
- `SMARTSTOCK_WALLET_SIGNING_PASSWORD` (legacy/plaintext override only)
- `SMARTSTOCK_WALLET_WWDR_CERT`
- `SMARTSTOCK_WALLET_PUBLIC_ORIGIN`

The public origin must be an HTTPS address that routes `/wallet/enroll/` to the store LAN service. Enrollment links expire after ten minutes and work once. Never commit the signing certificate, password, private key, or generated pass.

On Windows, store the signing password under the secure credential key
`apple-wallet-signing-password`; SmartStock reads it through the existing
CurrentUser DPAPI-backed credential store. The background server opens a separate
HTTP listener on `127.0.0.1:8447` only when the barcode configuration is complete.
Configure the public tunnel to use that loopback listener. It serves only
`/wallet/enroll/{token}` and returns 404 for every other path. Never point the
Wallet tunnel at LAN API port 8443.

For the installed background service, deploy the active PKCS#12 signing package
and WWDR certificate under the protected server-owned `.smartstock` profile tree,
not a desktop, Downloads, OneDrive, repository, or interactive-app-only folder.
Grant access only to the Windows account that runs the SmartStock server task.
The enrollment handler records sanitized local failures in
`.smartstock/wallet-enrollment-errors.log`; it never includes the enrollment token
or signing password. A secondary audit-write failure is logged locally but does
not discard an otherwise successfully signed pass response.

True NFC is disabled by default. Enable it only after a certified Apple VAS provider and reader have been validated. The additional settings are `SMARTSTOCK_WALLET_NFC_ENABLED`, `SMARTSTOCK_WALLET_NFC_PROVIDER`, `SMARTSTOCK_WALLET_NFC_PUBLIC_KEY`, and `SMARTSTOCK_WALLET_TRUSTED_READERS`. The ACR122U remains for physical SmartStock NFC cards and cannot read Apple Wallet VAS passes.

Before production use, verify the exact pass on an iPhone, scan it with the deployed barcode scanner, test required-PIN and badge-only policies, revoke it, and confirm that the revoked pass can no longer sign in.

## Wallet template editor

In Company Preferences, beside the printed badge editor, choose **Open Apple Wallet
Template Editor**. Wallet settings save independently for the selected store and
do not modify the printed badge. Saving requires the existing server-enforced
company-preferences permission and creates a security audit event.

When opening the unchanged default template, the editor seeds the draft with the
company name and logo from the Company Preferences form. Customized saved Wallet
templates are preserved. **Pull existing company info** explicitly refreshes the
name/logo and adds missing company address, phone, email and motto as hidden
Details fields; tick Show for the fields you want on the pass. Existing field
contents/layout are preserved, including previously imported contact fields.
This loads a draft only: choose Save Wallet template to persist it. Company
settings changed afterward are not automatically pushed into issued passes.

Choose employee information, edit labels, toggle visibility, reorder fields, and
add custom text. Name, first/last name, username, role, location, email and phone
come from the same employee badge data. Import badge branding/text copies the
company name, logo, quote, instructions and custom text into the draft. PINs,
physical badge identifiers and internal database IDs are deliberately unavailable.

Wallet controls card dimensions, text size, and layout. The editor offers one
primary field, up to three header fields, four combined secondary/auxiliary fields,
and additional Details fields. The QR code is always present. Preview zoom is not
a pass-size setting and the preview is approximate, not a device screenshot.

Choose a logo and an extra thumbnail image, or use the employee's existing photo
in the thumbnail slot. Images are proportionally fitted to 160×50-point logo and
90×90-point thumbnail slots, with 1x/2x/3x PNG assets. Image percentages resize the
content within the slot; arbitrary extra image layers are not supported. Imported
images are normalized and embedded in the template, so a register file path or
arbitrary URL is never sent to the server as a Wallet template image. Existing
employee photos use the established badge image loader; verify availability on
the server. Apple Watch may omit images.

Templates are read at issuance time, using the enrollment's store. Old links
created before store binding use the employee's first assigned store. Changes do
not update already-installed passes: replace/re-enroll the badge to apply them.
The existing company-customization row synchronization and recovery pipeline
carries `wallet_template_json`; no separate register database access is introduced.
Deploy the ordered migration on local/cloud provisioning paths before use.

## Poster background and CEO signature

Choose a background or use **Use employee badge CEO signature** to enable the
iOS 27+ Poster Generic presentation. The signature is copied from the current
printed badge's signature image; it is never generated or inferred from a name.
Missing images produce an error instead of silently substituting a signature.
The image is embedded into the lower-right artwork in a white readability panel,
above the approximate reserved native QR/footer area. It is not a native footer
image and is not placed below the QR. Size can be adjusted or the signature removed.

Artwork uses Apple's 358×448-point canvas and 1x/2x/3x PNG assets. Backgrounds
fill the canvas proportionally and may be cropped. A separate `primaryLogo` is
provided for poster mode. The standard `generic` layout remains in the signed
pass for older devices; those devices do not display poster artwork/signatures.
Secondary and auxiliary fields become one footer summary with their full values
also in Details. Employee thumbnails remain a standard-layout-only feature.

The editor's reserved-area guide is approximate: verify exact signature visibility,
text contrast, cropping and QR scanning on the target iPhone before rollout. Apple
controls native overlays, so final position/visibility cannot be guaranteed by the
Swing preview. Importing company information preserves existing poster settings.

## Certificate preparation while Apple enrollment is pending

On Windows, run `tools/prepare-wallet-certificate.ps1 -Email YOUR_CONTACT_EMAIL`.
This creates a new RSA signing key and certificate request outside the repository,
under `%LOCALAPPDATA%\SmartStock\wallet-signing`. It refuses existing output folders.
The folder is restricted to the current Windows user and its random keystore
password is protected using CurrentUser DPAPI. Do not run this under another
account expecting the store service to read it automatically.

Upload only `wallet.certSigningRequest` when Apple asks for the certificate request.
Keep `wallet-signing.p12` and `signing-password.dpapi` private. The temporary
self-signed certificate is not an Apple pass certificate. After approval, import
Apple's signed certificate into this same key entry, obtain the matching WWDR
certificate, and configure access for the actual service identity through a
separate protected deployment step. Never commit or send the keystore/password.

Enrollment addresses must be HTTPS origins without credentials, paths, queries,
or fragments (for example `https://badges.example.com`). Viewing the enrollment
page does not consume its token: the employee must submit the confirmation form.
Failed downloads after issuance require an administrator to issue a new link.

Automated pass tests use ephemeral self-signed certificates to verify the manifest,
detached signature, tamper detection, QR payload, and disabled NFC behavior. They
do not prove Apple acceptance, production certificate validity, database concurrency,
or deployed PIN/lockout/revocation behavior. Complete those tests before rollout.
