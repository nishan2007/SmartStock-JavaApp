# SmartStudio

## Reuse this computer's SmartStock pairing

On first launch, SmartStudio can reuse the current Windows user's saved SmartStock
register connection and certificate pin. The server verifies the register's valid
credential and matching computer, then enrolls a separate Studio key on the same
Device Management row. No server address or pairing phrase is needed.

**Allow Studio** still controls access. If it is disabled, enable it on the existing
computer row and click **Connect / check approval** without entering a phrase.
Staff still sign in separately; register employee sessions and database credentials
are never imported. Without a valid saved register pairing, use manual connection.
This requires both the updated SmartStock server and SmartStudio client.

## Drop fix and automatic reopening (0.1.4)

Native drag-and-drop data is captured before Windows completes the drop, while
file reading and decoding still run in the background. This fixes the DnD state
error in 0.1.3. An in-app update now reopens SmartStudio after installation,
including an update started by 0.1.3. Staff sign in again after the restart.

## Image drops and copying (0.1.3)

Drop one PNG or JPEG into **Original image** to load its preview, then click
**Remove background**. Connected network-drive files use the same bounded,
background reader as **Choose image**. Image transfers from other applications
are also accepted within the existing size and dimension limits.

**Copy result** puts the original-size result on the clipboard, with transparent
PNG data and a standard image format. Paste into an application that accepts
images; transparency support depends on the receiving application. Preview
background colours do not become part of the copied result.

The remover does not save an image history or store originals/results in the
server database or permanent image storage. The client holds the current images
temporarily until another image is loaded, sign-out, or closing the app. Saved
PNGs remain in the chosen folder; copied images remain on the system clipboard
until replaced.

## In-app updates (0.1.2)

After staff sign-in, **Check for updates** checks SmartStudio releases through
the paired SmartStock server. It offers the release notes, downloads the Windows
installer only after confirmation, verifies the exact size and SHA-256, then
asks before closing the app and launching the per-user update installer.
The existing install folder, pairing/preferences and shortcuts are retained.
Internet is required to check/download a published update; removal stays offline.

SmartStudio release rows use `platform=windows` and artifact paths beginning
`smartstudio/windows/`, ending `.exe`. SmartStock's feed explicitly excludes
`smartstudio/` paths; Studio's feed includes only those paths. No cloud schema
change is needed. Publish only after verifying the uploaded installer download,
size and SHA-256. SmartStudio 0.1.2 is published, and old server releases
do not support this feed separation. The new server/client code must be shipped
together for the first update. The initial installed client still needs a manual
upgrade to obtain this updater; subsequent releases can be installed in-app.

Only Windows installer updates are currently supported. A compiled development
launch can check/download, but installation requires the installed desktop app.
The published installer was downloaded in full and verified. An installed
updater handoff/reinstall was checked in a temporary fixture. Live paired
staff-session update checks at the store remain a separate deployment check.

SmartStudio uses its own white paintbrush icon on the existing Deckers colour
tile, with a purple ferrule. The desktop executable, shortcuts and application
window share this identity. Company Preferences still supplies the header logo.
PNG assets and a seven-size Windows ICO (16–256 px) live under
`src/main/resources/gy/deckers/studio/icons`. To regenerate them from the vector
drawing, run `java tools/GenerateStudioIcons.java` from this folder.

A dedicated Java 17 staff desktop client for SmartStock. Uses shared Deckers
Swing styling and the store's Company Preferences name, motto, and logo on the
login screen after secure pairing. Staff use their existing username/email/badge
and password/PIN.

The server removes backgrounds with offline IS-Net (Fast) or BiRefNet General
(Best quality). Images
travel over the paired HTTPS LAN API, never a public background-removal service.
PNG/JPEG inputs are limited to 6 MB and 12 megapixels. Results retain their
dimensions and can be saved as transparent PNG files.

## Quality improvements (0.1.2, server 1.0.236)

The quality selector defaults to Best quality when the server reports its model
installed. Older servers expose Fast only. Clean edges applies conservative,
colour-guided mask refinement and background-colour cleanup in uncertain edge
pixels; turn it off and reprocess to compare. Opaque interior pixels retain their
original colours, and existing source transparency is respected. White, black,
and checkerboard preview backgrounds help inspect the result; they are never
included in the exported transparent PNG.

Both models currently examine a 1024-square image, with each model's own
preprocessing. Best quality changes the segmentation model, not the original
image resolution. Both exports keep the input dimensions. Quality depends on
the subject and photo; glass, hair, low contrast and shadows still need review.
The Best model is 972,666,916 bytes and can take substantially more server memory
and CPU time. The client allows five minutes for studio requests. Models are
pinned by SHA-256, and missing/corrupt models return an explicit error. There is
no runtime download or external image-processing service. The server continues
to process one Studio job at a time.

Future Windows/macOS server bundles stage both models using
`SmartStock/tools/stage-studio-models.ps1` or `.sh`, with their license notices.
The Windows release and installers include these changes. Installed apps remain
on their earlier version until the update is applied.
The erase/restore brush is a separate future improvement.

## Build and run

From the repository root:

```powershell
mvn -q -f SmartStock/pom.xml install
mvn -q -f SmartStudio/pom.xml test package
java -jar SmartStudio/target/smartstudio-0.1.2.jar
```

Keep `target/lib` beside the JAR. For a portable Windows app with its own Java
runtime, run `SmartStudio/tools/package-windows.ps1` after building. Add `-Installer`
to also create `target/SmartStudio-setup-0.1.2.exe` using Inno Setup. The installer
includes Java, installs for the current Windows user, and creates Start menu and
desktop shortcuts. It does not install or change the SmartStock server.

## First connection

Use Find store servers, or enter the server computer's address and HTTPS API port
(normally 8443). Enter the server's pairing phrase, enable **Allow Studio** on the
computer's existing row in SmartStock Device Management, then check approval and
sign in. Only a new physical computer creates a new device row. This does not
require studio.deckers.gy or browser certificate installation.

The server must run a build containing `/v1/studio/branding` and
`/v1/studio/remove-background`, with its offline model installed. SmartStock
1.0.235 contains device-row linking and Studio access controls; apply that server
update before using SmartStudio 0.1.1. Computers paired using 0.1.0 must pair once
again with a fresh phrase to attach the Studio credential to the existing row.
The obsolete Studio row is retained for history but hidden from the device list.
This app does not install or update the server automatically.

Credentials and pairing are isolated under `%USERPROFILE%/.smartstudio/.smartstock`
and use SmartStock's existing Windows DPAPI protection. No register credentials,
database passwords, or server configuration are copied. Client mode is forced.
Production is the default; launch with `-Dsmartstock.environment=development`
before `-jar` only for a development server. Server permissions and session expiry
apply; the app also honors the staff session's inactivity timeout.

Network drive images are streamed into bounded local memory before previewing;
there is no separate remote file-size lookup. Inaccessible files show an explicit
message with read-access/network-drive guidance. Loading and removal show progress.
Selecting a new image clears the previous result, so a failed load cannot process
the wrong image.

Native pairing/login and end-to-end removal must be verified against the updated
server at each store. Isolated database and installed-app tests do not prove access
to a specific remote network share.
