# Permanent SmartStock installer downloads

The page at `https://downloads.deckers.gy/` and the direct addresses
`/download/windows` and `/download/mac` are public downloads. They require no
SmartStock account, store authentication, store origin configuration or online
store server. The R2 bucket remains private; only the selected installer objects
are exposed through these routes. Arbitrary bucket paths and update ZIPs are not
publicly served.

Operator uploads and publication under `/_release/` still require the protected
`INSTALLER_PUBLISH_KEY`. Publication downloads and verifies the complete artifact
before changing the selected installer. Each installer request checks its size,
SHA-256 metadata and ETag. Public downloads do not grant register access or
publication privileges.

## Activation

Run the existing deployment tool with the protected publisher configuration.
The Worker needs the account, custom domain, private `UPDATE_BUCKET` binding and
publisher secret. Store origin keys, authentication endpoints and login rate
limiter bindings are no longer needed for downloading. Existing legacy bindings
may remain on a deployed configuration without affecting downloads.

Run `npm test`, deploy, then check unauthenticated GET and HEAD requests to the
page and installer. Confirm the response is a file attachment, the selected
artifact matches its release record, and publication without the operator key is
denied. Installed Windows and macOS applications require separate checks.

## Windows release workflow

For 1.1.1 and later, follow [packaging and publishing](../../docs/release-packaging-publishing.md).
Application ZIPs exclude model weights and app publishing checks the hosted,
verified model catalogue first. Protected multipart routes also accept private
`models/<fast|best>/<sha256>.onnx` uploads; deploy this Worker update before the
first Best model publication. Model uploads never change a public installer
selection and cannot use the installer `publish` route. The existing signed
update-download Worker serves the private models to the store server.

`tools/deploy-installer-portal.ps1` deploys the portal using the existing protected
website configuration. Production origin identities stay outside the checkout.
It saves the random operator key encrypted with Windows DPAPI in the production
profile as `installer-publish-key.dpapi`. For multiple configured origins,
provide `SMARTSTOCK_INSTALLER_ORIGIN_KEYS_JSON` containing each dedicated key.
Add every store's verified origin before claiming all stores are activated.

Build and publish the update ZIP and matching all-in-one installer together:

```powershell
./tools/package-windows-release.ps1 -Publish
```

For a prebuilt release:

```powershell
./tools/publish-r2-update-windows.ps1 -Artifact target/release-windows/smartstock-windows-1.0.229.zip -Version 1.0.229 -BuildNumber 100229 -ReleaseNotes release-notes-1.0.229.txt -Installer target/release-windows/smartstock-windows-setup-1.0.229.exe
```

The existing publisher discovers the all-in-one installer beside the update ZIP
when `-Installer` is omitted. A register-only installer must be selected
explicitly. The update ZIP is verified before its app-release row is published;
the installer is independently downloaded and verified before the permanent
portal link changes. These are separate publications: a portal failure after
update publication does not roll back the app-release row. Retry only installer
publication in that case:

```powershell
./tools/publish-installer-windows.ps1 -Installer target/release-windows/smartstock-windows-setup-1.0.229.exe -Version 1.0.229 -BuildNumber 100229
```

For macOS, `tools/publish-r2-update.sh` accepts the installer as its sixth
argument and discovers `smartstock-mac-<version>.dmg` beside the ZIP by default.
Provide `SMARTSTOCK_INSTALLER_PUBLISH_KEY` in the release operator environment.
No operator key is bundled in SmartStock. The default publisher URL is
`https://downloads.deckers.gy`; override with `SMARTSTOCK_INSTALLER_PORTAL_URL`
only when deliberately publishing to another deployment.

If no installer exists beside an update and none is selected, publication is
explicitly reported as update-only and the portal selection remains unchanged.
The Windows packaging `-Publish` option always passes the installer it built.
