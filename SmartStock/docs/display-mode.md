# Work Queue Display Mode

Choose **Display** in Guided Setup, or launch SmartStock with --display.
Exit fullscreen and choose **Connection / Pair Display**. Configure the local Store Server and enter its temporary pairing phrase.
On the server, approve the device marked **[Display]** in device management.
The display checks for approval automatically. Subsequent launches need no employee login.
An existing approved register keeps its server-approved role; changing local mode does not change permissions.
To repurpose a register, remove its approval, pair in Display mode, then approve it again.

The screen shows internal staff details for the paired store and is unsuitable for public customer use.
It refreshes every 15 seconds, retains marked stale data through outages, and clears data when credentials are rejected.
F11 or the on-screen button toggles fullscreen; Escape exits fullscreen.
Connection/pairing and Close App controls appear in windowed mode.
The footer shows the running SmartStock version in both windowed and fullscreen mode.
Exit fullscreen and choose **Update Now** to check immediately and install an available update, then restart.
Manual checks use the same verified updater as automatic checks; an update already running is not started again.
The footer reports progress, an up-to-date result, or the failure and any required installer repair.
Overflowing lanes scroll automatically after 15 seconds without pointer interaction, pause at the bottom for four seconds, and restart.
Closing the window stops refresh and scrolling. OS startup is configured separately.

The server must run this version before pairing a Display device. Local/cloud schema contracts and the ordered migration include DISPLAY.
Display credentials authorize only their store queue, images referenced by that queue, and device credential maintenance.
No PostgreSQL or Supabase credentials are provisioned to the display.

Manual acceptance: on an installed Windows app, pair and approve a screen, restart without login, toggle fullscreen on each monitor,
check long/empty queues and pointer pauses, disconnect/reconnect the LAN, block the device and verify data clears, then close the app.
Repeat fullscreen checks on macOS. No live-store installation or service changes are part of implementation validation.

Display mode checks updates at startup and hourly. On Windows, run the new standard installer once as administrator to register the protected `SmartStockDisplayUpdates` maintenance task. Later automatic updates run without UAC: the SYSTEM task downloads only the official selected Windows installer, verifies its size and SHA-256 against the HTTPS portal manifest, rejects downgrades, then installs silently. The interactive user's separate watcher reopens SmartStock after completion or failure. Registers and servers retain their existing update behavior.

The task's scripts/configuration/status live in `%ProgramData%\SmartStockDisplayUpdates`, writable only by SYSTEM and Administrators. Users may trigger the fixed task, but cannot supply commands, executable paths, download URLs, or updater manifests. Reparse points and unsafe existing service directories are rejected. Only protected Program Files installations are supported. Uninstall removes the task; logs remain for diagnostics.

The installer portal must deploy the new read-only `/manifest/windows` endpoint before this feature can operate. Missing metadata, connectivity failures, checksum mismatch, and installation failure postpone the update or reopen the old app. An installer execution failure can require manual repair; no rollback guarantee is made for the full installer path. The first installer enables the task; app-only update ZIPs cannot enable it. Existing installations therefore need that one-time administrator installer run.

Required Windows acceptance in an isolated installation: verify task/file ACLs using a standard user, request a valid official update without UAC, confirm app restart under the original user, reject modified downloaded bytes and stale metadata, disconnect during download, test installer failure, and uninstall the task. Do not run these against the live store. This source change does not deploy the portal, publish an installer, register a live task, or prove installed behavior.
