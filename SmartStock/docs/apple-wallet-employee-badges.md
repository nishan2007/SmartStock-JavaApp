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

True NFC is disabled by default. Enable it only after a certified Apple VAS provider and reader have been validated. The additional settings are `SMARTSTOCK_WALLET_NFC_ENABLED`, `SMARTSTOCK_WALLET_NFC_PROVIDER`, `SMARTSTOCK_WALLET_NFC_PUBLIC_KEY`, and `SMARTSTOCK_WALLET_TRUSTED_READERS`. The supported ACR122U integration is for physical SmartStock NFC cards; Apple Wallet VAS badge reading has not been validated on this reader.

Before production use, verify the exact pass on an iPhone, scan it with the deployed barcode scanner, test required-PIN and badge-only policies, revoke it, and confirm that the revoked pass can no longer sign in.

## Read-only reader diagnostics

Compile the standalone presence diagnostic with Java 17 or later. Run it on the
computer physically connected to the reader, using its installed Java runtime:

```powershell
javac -d "$env:TEMP" tools/NfcReaderDiagnostics.java
java -cp "$env:TEMP" NfcReaderDiagnostics 60
```

The optional duration is 0 through 120 seconds; omit it for a single snapshot.
The tool lists all PC/SC readers and reports contactless-device presence changes.
For ACR122 readers it also queries the firmware and polling configuration using
the read-only reader escape commands documented in the
[ACS API manual](https://downloads.acs.com.hk/drivers/en/API-ACR122U-2.02.pdf),
sections 6.3/6.4 and Appendix A. If the driver blocks these commands, the tool
reports the limitation without changing registry entries or reader configuration.
It sends no card commands, writes no cards, and reads no UID, badge credential or
payment data. A phone waking Wallet or appearing as a contactless device does not
verify VAS support or authenticate an employee. A missing presence event does not
prove that the hardware cannot communicate with a phone through another protocol.

Keep NFC issuance disabled until an Apple-issued NFC pass certificate, a validated
reader/provider integration and server-side presentation verification are in
place. The provider adapter is currently a disabled boundary, not an implemented
VAS transport. Setting environment variables alone cannot enable it.

Reference requirements: [Apple contactless passes](https://developer.apple.com/wallet/loyalty-passes/)
and [ACS WalletMate specifications and VAS tooling](https://www.acs.com.hk/en/products/548/walletmate-mobile-wallet-nfc-reader-apple-vas-google-smart-tap-certified/).

## Experimental ACR122U VAS discovery

`services.ExperimentalWalletVasProbe` is a separate command-line experiment, not
the production `WalletNfcReaderAdapter`. Compile it with Java 17:

```powershell
javac -d "$env:TEMP\wallet-probe" src/services/ExperimentalWalletVasProbe.java
java -cp "$env:TEMP\wallet-probe" services.ExperimentalWalletVasProbe 60
java -cp "$env:TEMP\wallet-probe" services.ExperimentalWalletVasProbe 60 --raw
```

Run one command at a time with exactly one ACR122 reader connected. The first uses
PC/SC ISO7816 exchange; `--raw` wraps the same SELECT in the ACS direct-transmit
command and PN532 InDataExchange. Both rely on normal reader polling to establish
target 1 before exchanging commands. Neither implements Apple ECP polling or
automatic pass presentation. Unlock the phone, hold it against the reader, and
remove it between attempts. A session with zero attempts means PC/SC never
reported a device; it says nothing about VAS SELECT acceptance.

The only phone command is SELECT for `OSE.VAS.01`. Response status and a bounded
BER-TLV application-label check are logged; raw phone responses, UIDs and
credentials are not. A successful SELECT with an `ApplePay` label establishes
discovery only. There is no GET DATA, pass decryption, replay verification or
employee authentication. No production NFC gate is enabled by this tool, and it
does not change reader settings, drivers, registry entries or store data.

Transport commands are documented in the ACS API manual. The VAS discovery
command and example response are based on the original
[kormax Apple VAS protocol research](https://github.com/kormax/apple-vas), not an
Apple-certified implementation or proof that this reader is VAS-certified.

## Manager overrides with Wallet QR badges

The shared manager approval dialog accepts a Wallet QR scan in the
**Manager Username / Email / Wallet Badge** field. Keyboard-wedge scanner framing
is normalized the same way as register login. The prompt switches to an employee
PIN label; supply the manager's employee PIN when company badge security requires
it, or leave it blank when the company permits badge-only authentication. A scan
does not submit the approval automatically. Enter the required override reason
and choose OK.

The server authenticates the active Wallet credential at the requesting
register's store using the same revocation, active-employee, store-membership and
PIN checks as Wallet login. It then enforces the requested override permission
and issues the existing device/requester/action/resource-bound five-minute
approval token. A Wallet badge never grants permissions by itself. Physical NFC
badge and username/password approval remain available. This adds barcode
approval, not Apple Wallet NFC approval.

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
