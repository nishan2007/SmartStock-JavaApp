# Independent AI model updates

SmartStock application and installer packages omit the Fast and Best ONNX model
files. The ONNX runtime, model notice and licences remain in the application.
An administrator opens **Status > AI Models** to install, check for updates, or
repair either model. Each model downloads independently from Deckers hosting.
No model is downloaded automatically. After installation photo processing works
offline on the store server. Registers and SmartStudio send photos to that server.

## Storage and compatibility

Models live at `~/.smartstock/profiles/<environment>/ai-models`, under the same
server account and `user.home` used by the desktop and background service. They
are outside application, update staging and rollback directories. Files are named
by SHA-256; each quality has an atomically replaced active descriptor. Prior
versions are retained. Changing the service account/home requires transferring
this directory with the store profile; a register's profile is not the server's.

The schema-v1 catalogue contains exactly one entry for Fast and Best, with
`quality`, `version`, `objectKey`, `sizeBytes`, `sha256`, and `compatibility`.
Object keys are `models/<fast|best>/<sha256>.onnx`. Supported inference contracts
are `isnet-1024-v1` and `birefnet-1024-imagenet-v1`. New weights must conform to
those input/output and preprocessing contracts; a different architecture requires
an application change and a new compatibility identifier. Keep notices/licences
current when changing weights.

The server caches the last supported catalogue. Installed models remain usable
when hosting is unavailable. A download checks the hosted catalogue, validates
the selected entry, checks byte size and SHA-256, and atomically activates the
verified file. Failed, truncated or interrupted downloads leave the previous
active descriptor intact and remove partial files. Retry restarts the download;
v1 does not resume partial transfers. Same-quality duplicate requests share one
job; a filesystem lock also prevents another server process installing it at the
same time. Different qualities can download independently. In-flight inference
continues against its original immutable file.

## Hosting and publishing

Use the existing private `smartstock-updates` R2 bucket and update-download
Worker. Configure `SMARTSTOCK_UPDATE_R2_WORKER_URL` and
`SMARTSTOCK_UPDATE_R2_SIGNING_SECRET` on the **server** using its protected
`r2-update.properties` or environment. No hosting secret or signed URL is returned
to registers. The update-download Worker already streams signed objects and
needs no change. The protected installer portal must receive the multipart model
upload changes before the first Best model publication. No database migration is
required for model storage.

The release operator needs Node.js and the update Worker's existing Wrangler
dependency and authenticated Cloudflare publishing access. Stage/download the
existing pinned model files with `tools/stage-studio-models.ps1` (Windows) or
`tools/stage-studio-models.sh` (macOS), outside the application packaging input.
Use the checked-in catalogue for the initial publication:

```text
node SmartStock/tools/publish-studio-models.mjs SmartStock/src/models/catalogue-v1.json <staged-model-directory>
```

Best exceeds the current Wrangler 300 MiB upload limit. It uses the portal's
protected multipart uploader with the existing `SMARTSTOCK_INSTALLER_PUBLISH_KEY`;
Windows can load that key through `tools/publish-studio-models-windows.ps1`.
Fast and the small catalogue files use Wrangler. The publisher verifies supplied
files before uploading anything. It reuses
verified remote objects, uploads missing objects, downloads them again, and
verifies their size and SHA-256. It then uploads and verifies an immutable
catalogue snapshot before replacing `models/catalogue-v1.json`, and reads back
that live catalogue. A directory can contain only the changed model if the
other catalogue entry is already hosted and passes download verification.
Normal SmartStock release publishing never uploads these model files or changes
the model catalogue. The model publisher does not modify app release metadata.
App publication checks the catalogue and its verified immutable snapshot first.
See [packaging and publishing](release-packaging-publishing.md) for the full
workflow and the 1.1.1/build 101001 release commands.

Authenticated POST LAN interfaces are `/v1/studio/models/status`,
`/v1/studio/models/refresh`, and `/v1/studio/models/install` (body: `quality`).
All require a paired device, valid employee session and server-verified ADMIN
role. Status includes availability, installed/available version, size, action,
and job state/byte progress. Install returns immediately; status polls the job.
Photo-review requests use `output: "review-jpeg"` on the existing authenticated
photo-processing endpoint; existing PNG clients retain their behavior.

## Migration and rollout

1. Publish and verify the separate model objects and catalogue **before** making
   the first model-free application release available. Verify the store server's
   signing configuration using an administrator's model catalogue refresh.
2. Windows' downloaded updater and the new macOS downloaded updater verify and
   preserve legacy models from both desktop and service copies before replacing
   app files. Migration failure aborts the update without deleting originals.
3. **Older macOS native updaters run the installed application code**, so they
   cannot execute this new pre-update migration on their first upgrade. Before
   updating those installations, run the small, JDK-only
   `smartstock-ai-model-migration.jar` emitted beside the release artifacts, with
   the server's bundled Java and home. Stop at any migration error:

   ```text
   <server-java> -Duser.home=<store-server-home> -jar smartstock-ai-model-migration.jar <installed-app-jar-directory> production <service-app-directory>
   ```

   On macOS the app JAR directory is normally
   `/Applications/SmartStock.app/Contents/app`; inspect the actual bundle. Omit
   the service argument if no separate service copy exists. Use `development`
   for a development profile. The helper copies verified files and retains the
   originals. Distribute/check the helper's SHA-256 alongside the release.
   Subsequent updates run the new updater directly from the verified release.
4. Inspect update ZIPs and installer app images for absence of `.onnx` files and
   presence of runtime/notices/licences. The historical 1.0.221/1.0.223 patch
   packagers are retired because their old application code expects bundled
   models; use the normal current Windows release packager.
5. Verify on installed Windows and macOS applications that desktop/service
   paths agree, existing models survive update and rollback, administrator-only
   downloads work, and Fast/Best produce usable photos offline. Packaging and
   unit tests do not substitute for these installed-app and service checks.

No publication or deployment is performed merely by building these changes.

## Validation commands

```text
mvn -q -f SmartStock/pom.xml test
node --test SmartStock/tools/publish-studio-models.test.mjs
node --test SmartStock/cloudflare/smartstock-update-download/test/worker.test.mjs
git diff --check
```

Run `SmartStock/tools/security-check.sh` through Git Bash/WSL on Windows.
Use the platform's normal packaging script with a separate validation output
directory and without its publishing option.
