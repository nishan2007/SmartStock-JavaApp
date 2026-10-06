# SmartStock packaging and publishing

`pom.xml` is the shared SmartStock version source. Windows and macOS use the
same version and commit. Build numbers use `major * 100000 + minor * 1000 + patch`:
SmartStock **1.1.1** is build **101001**. SmartStudio has its own version/channel;
when rebuilding it, use the current SmartStock client dependency.

## Prepare an application release

From the repository root, inspect Git status and preserve unrelated changes.
Update the Maven version and matching `SmartStock/release-notes-<version>.txt`.
Run Maven tests, the repository security check, and the whitespace check.

On Windows, build the standard all-in-one installer and updater ZIP without
publication:

```powershell
./SmartStock/tools/package-windows-release.ps1 -OutputDirectory ./SmartStock/target/release-windows-1.1.1
node SmartStock/tools/verify-app-update.mjs SmartStock/target/release-windows-1.1.1/smartstock-windows-1.1.1.zip 1.1.1
```

Use `-UpdateOnly` for an update ZIP only. Use `-RegisterOnly` when a separate
register installer is needed; build it in a separate output directory so the
exact standard release artifacts and checksums remain intact. The normal
all-in-one installer also supports register setup. Neither build installs or
updates the running store.

On macOS, run `bash SmartStock/tools/package-macos-release.sh`. Inspect the app
bundle, update ZIP, and DMG. Build/install validation must take place on macOS;
a Windows build or Bash syntax check does not validate a Mac release. If signing
or notarization changes artifact bytes, verify and publish those final bytes.

Every application update/installer excludes `.onnx` model weights, retains the
ONNX runtime and licences, and uses persistent profile models. All packaging
entrypoints enforce the split. The historical catalog/photo-review patch
packagers are retired. The generic Bash register packager follows the same
split and build-number convention; prefer native packaging for distribution.

Package output includes `smartstock-ai-model-migration.jar`. Save its SHA-256
alongside the exact release artifacts and distribute it to older Macs before
their first model-free upgrade. Follow [AI model migration](ai-model-updates.md).

Test the installed app and service in an isolated profile, including model
migration, independent Fast/Best installation, offline processing, administrator
enforcement, update and rollback. Verify relevant database and hardware flows
separately. Record outstanding checks instead of treating a successful build as
proof of an installed release.

## Publish models separately

For the initial split, first deploy the updated **installer portal** Worker
with its existing protected operator key; its multipart routes now accept private
model objects. Its public installer selection remains independent. The existing
signed **update-download** Worker needs no change for model downloads.

Stage models outside application input, then publish them separately:

```powershell
./SmartStock/tools/stage-studio-models.ps1 -Destination ./SmartStock/target/release-ai-models
./SmartStock/tools/publish-studio-models-windows.ps1 -ModelDirectory ./SmartStock/target/release-ai-models
```

On macOS use the Bash staging tool and `node SmartStock/tools/publish-studio-models.mjs
SmartStock/src/models/catalogue-v1.json <model-directory>`. Supply the existing
protected `SMARTSTOCK_INSTALLER_PUBLISH_KEY` only in the operator environment.
Windows reuses its encrypted `installer-publish-key.dpapi`. Both platforms also
need the update Worker's local Wrangler dependency and authenticated Cloudflare
operator access for object verification and small catalogue publication.

Wrangler object uploads are capped at 300 MiB in the currently installed tool.
Fast can use that path; Best uses the existing protected multipart uploader.
The model publisher downloads and verifies all referenced model objects before
publishing the immutable catalogue snapshot and live catalogue. It reuses valid
remote objects. For a later model-only release, change its catalogue entry and
supply the changed file; no SmartStock app version bump is needed while its
inference compatibility contract remains supported.

Models, signing secrets, DPAPI files and verification downloads never belong in
Git. Application publishing must not stage or upload model weights.

## Publish the prepared application

Publication is a separate action after installed-app acceptance. The publishing
tools verify archive layout, matching app version, model-free content, runtime/
licences, model-management/migration support, and the hosted catalogue snapshot.
They then retain the existing full download/size/SHA-256 verification before
creating app-release metadata or changing the selected installer. Missing model
hosting blocks app publication; an app publisher never publishes models for you.
The catalogue readiness check transfers only catalogue JSON, not the large models.
The model publisher verifies complete model bytes; runtime downloads verify them
again before activation. Still test real administrator downloads before rollout.

For prebuilt Windows 1.1.1, from `SmartStock`:

```powershell
./tools/publish-r2-update-windows.ps1 -Artifact target/release-windows-1.1.1/smartstock-windows-1.1.1.zip -Version 1.1.1 -BuildNumber 101001 -ReleaseNotes release-notes-1.1.1.txt -Installer target/release-windows-1.1.1/smartstock-windows-setup-1.1.1.exe
```

Alternatively, `package-windows-release.ps1 -Publish` builds and publishes when
publication is explicitly requested. Use the protected production publisher
configuration. macOS uses `publish-r2-update.sh <zip> 1.1.1 101001 mac <notes> <dmg>`
with the operator's protected configuration.

Verify published metadata against exact size, SHA-256, version, build and object
path. The app ZIP and permanent first-install selection are separate publications.
If installer publication fails after the app row succeeds, retry only the
installer publisher. Never rebuild different bytes under a published version.
Update the store server first, then registers. Publishing does not update or
restart running installations.
