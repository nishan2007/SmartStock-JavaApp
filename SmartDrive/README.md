# SmartDrive

SmartDrive is the Smart Suite file-sharing product. DeckersDrive is the
configured company brand for the Deckers installation.

Set `installationName` once to name the SMB share, data directory, and
OneDrive backup folder. The staff-facing name defaults to the same value.
Set `displayName` only if staff-facing labels need a different spelling.

From this directory, run `npm run branding -- config.example.json` to see
the resolved names and paths. The example contains no credentials. Run
`npm test` to validate the branding configuration.

A display-only rename keeps existing paths. Changing an installation name
after setup requires moving the existing data and updating the share, saved
drive mappings, and backup destination together. The branding command
resolves settings without provisioning or moving storage.

## File API development

With no originTls settings, the listener is HTTP on guest loopback only. Use this
when the connector runs inside the guest. When the existing connector runs on
the physical host, configure a reserved guest LAN address and HTTPS instead:

```json
"originTls": {
  "listenAddress": "192.168.1.60",
  "allowedAddresses": ["192.168.1.10"],
  "certificatePath": "C:\\ProgramData\\SmartDrive\\TLS\\origin.pem",
  "privateKeyPath": "C:\\ProgramData\\SmartDrive\\TLS\\origin.key"
}
```

These are examples, not discovered production addresses. allowedAddresses lists
the exact private source IPs of approved connectors/origin clients, including the
SmartStock server only if it directly uses this origin. Wildcard/public binds,
CIDR ranges, empty/duplicate clients and verification-disable options are rejected.
The service requires TLS 1.2 or later and rejects unapproved peers before employee
or bridge authentication. File/store/employee/manager/CSRF checks still apply.
Origin certificates and keys must be separate, valid matching local files outside
staff storage, with no links or hard links. They remain outside source/release
payloads and Git; the config contains paths only. Expired/invalid pairs prevent
startup. Rebranding rebases in-root TLS paths while external certificate paths
and technical DNS identities remain stable.

Provide a server certificate for the origin hostname and its private key using
the approved company certificate process. First service installation validates
them before mutation and applies Configure-OriginTls.ps1. For a later TLS change
or renewal, stop SmartDrive and review that helper with ConfigPath, NodePath and
WhatIf, then run without WhatIf. It sets both files to SYSTEM/administrators full
access and LocalService read-only, and creates/updates only its own narrow
SmartDrive-Origin rule: specified guest IP/port, Node executable, approved remote
IPs and Private/Domain profiles. The application independently enforces the peer
list. It refuses an unrelated rule with the same name. No firewall rule is needed
for the loopback-only default. Installed LocalService access and actual firewall
behavior still require guest acceptance.

Merge cloudflare/tunnel-origin.example.yml into the existing connector routing
without replacing other routes. The hostname must match the origin certificate;
set originServerName and, for a private CA, caPool on the CONNECTOR machine.
Keep noTLSVerify false. A publicly trusted origin certificate can use the normal
trust store instead. Protect the private origin hostname with the Cloudflare
Access service-token policy used by the gateway. Confirm certificate validation
from the real connector and the public worker route before staff use. Reference:
https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/configure-tunnels/origin-parameters/

Settings displays the effective origin transport/listener/client IPs to managers;
certificate/private-key paths and bytes are excluded. Source tests exercise real
HTTPS trust rejection, trusted requests, peer denial and key/path restrictions.
The Windows fixture checks file ACLs and generated firewall scope without applying
live rules: powershell.exe -NoProfile -File SmartDrive/test/Origin-TlsPolicy.ps1.

Run `node src/main.js <installation-config.json>` on the VM. In addition to
the branding settings, the service uses `storeRoots` (an object mapping
SmartStock location IDs to absolute guest directories), `employeeServer`
(the HTTPS SmartStock employee server), and `employeeOrigin` (the HTTPS
origin accepted by that server). If the browser uses a different hostname,
set `publicOrigin` to the DeckersDrive HTTPS origin. Supply a persistent random 32-byte hex
`SMARTDRIVE_FILE_ID_KEY` through the service environment.

The loopback listener exposes read-only store, folder and preview endpoints
under `/employees/drive/api/`. The `/employees` prefix allows the browser to
send the employee cookies with their existing scope. SmartStock checks the
session and current store assignments on every file request.

Route the HTTPS hostname to the guest's loopback listener through the
approved ingress and open `/employees/drive/`. Browser activation uses
the existing employee activation token. Put it in the
`/employees/drive/#activate=...` fragment. Login, MFA verification, and logout
are forwarded only to the configured SmartStock employee service. Use
the store files page for direct image previews and a separate PDF viewer tab.

## Scanner intake

Configure `scanners` as records with a safe machine `id`, string `storeId`,
and private absolute `intake` path. For the Canon, use `Canon-C5040` as its
ID. Set `scanStateRoot` to a private directory outside the staff roots on the
same guest volume. Supply `defenderScanner` as the absolute path to the
installed Microsoft Defender MpCmdRun.exe.

The Windows worker polls every ten seconds, waits for unchanged file metadata,
claims the upload into private state, and takes an exclusive read lock while
copying. It checks PDF, JPEG, or TIFF content and runs Defender before publishing
into `Scans/<machine ID>` under that scanner's assigned store. Invalid files
go to private quarantine. Claimed jobs are retried after restart. Each intake
directory needs a distinct copier account and ACLs installed by the guest
administrator. Physical Canon delivery still needs verification.

The Management status page requires `MANAGE_SMARTDRIVE` from the current
SmartStock employee session. It shows capacity and scanner results for the
manager's assigned stores.

The management page can pause or resume processing for an assigned scanner.
Pause state survives restarts and the action is audited. Pausing leaves
received documents in private intake for processing after resume. Disabling
a copier's SMB account remains a separate administrator action.

## Guest and desktop management setup

After creating the 300 GB pilot disk, run `tools/Initialize-VM.ps1` from an
administrator PowerShell window on the physical host. Supply the existing
external Hyper-V switch name and licensed Windows installation ISO. The helper
creates a Generation 2 guest with two CPUs, 4 GiB RAM, a separate 80 GiB Windows
disk, and the existing data disk. It configures automatic startup and clean
shutdown and leaves the guest off for review before Windows installation.

Install the app tools in a persistent location. To add the management desktop
shortcut, run `tools/Install-ManagementShortcut.ps1 -Url <HTTPS management URL>`.
The optional `-Guest` switch starts the service and waits for Windows to report
it running when the shortcut runs inside the guest. Windows starts its account
service dependency as needed. The browser then follows the configured HTTPS
route, including any Cloudflare Access sign-in redirect. Service state alone
does not establish application or Tunnel health; inspect the signed-in console.
On the physical host or client PC,
the shortcut opens the dashboard URL. Use `-WhatIf` to review shortcut creation
and VM setup before running them.

## Versioned OneDrive backup

Configure `backupStateRoot` and `recoveryRoot` outside staff storage.
`backupSelections` is an array of objects with string `store` and `parts`
(folder path components relative to that store's root). The worker captures
selected folders every minute and retains pending snapshots across restarts.
It hashes both the upload source and downloaded verification copy before
marking a version verified.

Set `oneDrive` with the company's `tenantId`, `clientId`, `driveId`, and
dedicated `folderId`. Supply the application secret in
`SMARTDRIVE_ONEDRIVE_CLIENT_SECRET`. The Graph adapter writes a file version
and recovery metadata for each immutable backup ID. Failed uploads remain
pending for retry with backoff. Restores verify bytes before placing a new
file and original-path metadata in Recovery. Live company OneDrive access and
recovery still require deployment verification.

Managers can use the web Management section to list backed-up versions, retry
pending uploads, and restore a verified version into Recovery. Listing is scoped
to the employee's current stores. Restore and retry require the current
`MANAGE_SMARTDRIVE` permission plus SmartStock write-session authorization,
including the browser origin and CSRF token. Keep the SmartStock server updated
with the `auth/drive-authorize` route before enabling these controls.

DeckersDrive uses SmartStock's dedicated `auth/drive-login` path. Any active
employee can authenticate for Drive, including employees without permission to
use the other staff web apps. Existing browser approval, MFA, account-active
checks, and store assignments still apply. Management access requires
`MANAGE_SMARTDRIVE`. Other employee web routes retain their own permissions.

Managers can browse an assigned store folder and choose **Back up this folder**
or **Stop backing up this folder**. These settings are persisted in the private
backup state directory and survive restart. The management page lists selected
folders. Removing a selection stops future captures while retaining existing
backup versions. One unavailable selected folder does not prevent pending
versions from uploading.

Backup selection changes, retries, and restores record the requesting employee,
affected stores, request time, and outcome in private audit storage. The web
Management page shows recent activity available to the manager's assigned
stores. A management write requires working audit storage before proceeding.

The employee browser shows file type, byte-derived size and modified time from
current authorized filesystem metadata. Unsupported formats remain listed with
an unavailable-preview label. Linked files/directories and entries that disappear
or become inaccessible during listing are excluded; metadata requests do not
bypass the store/path authorization used for previews.

When an API session expires, the browser retains only an in-memory navigation
intent while clearing the visible workspace and credentials. After sign-in it
rebuilds that location from current authorized store/folder listings, refreshes
names and management flags, and restores a selected preview only if the current
file still appears there. An unavailable location falls back to assigned stores.
Saved labels/permissions never authorize access, and explicit sign-out clears
the intent even if the logout request fails. No private navigation history is
written to localStorage. Pure navigation/authorization tests cover fresh metadata,
changed assignments, missing folders and expired-session failure; browser QA of
the complete login flow remains required.

## Updating, repairing and rolling back the Windows service

Use the maintenance tool from a reviewed extracted SmartDrive source payload.
Keep the current Node runtime and installation JSON outside the application
directory. In an administrator PowerShell inside the guest, stop SmartDrive and
SmartDriveAccounts, then review:

```powershell
.\installer\windows\Update-Service.ps1 -Action Update -InstallRoot 'C:\Program Files\SmartDrive' -SourceRoot 'C:\Deploy\SmartDrive' -MaintenanceWindow -WhatIf
```

Run without `-WhatIf` to stage and activate. `-Action Repair` uses the same
verified-source workflow to replace damaged/missing application payload files;
use the source matching the intended installed version. This repairs payloads,
not missing credentials, lost data, Windows account/group damage or an altered
service registration. Restore those authorities from their protected backups
and review the installed service separately.

Before activation, the tool verifies registered service paths and stopped state,
checks for linked paths, protects a new sibling candidate directory, copies and
hash-checks the source files, compiles native services/preview runner, checks
JavaScript/PowerShell syntax and Node capabilities, and validates the existing
branding config. It does not start another live worker during staging. A private
payload manifest inventories code and binaries; operational secrets/account
journals are excluded from that inventory. Current service.json,
account-stores.json and scanner-accounts.json are carried forward with checksum
verification. The installation JSON, data tree, SMB shares, accounts, backup
versions and management shortcut paths stay in place.

Activation switches sibling application directories and retains the old one as
`<InstallRoot>.previous-<unique-id>`. If the second rename fails, the tool attempts
to restore the original directory and reports failure. If restoration also
fails, keep services stopped and inspect all paths and the protected maintenance
journal. Candidate build/validation failures retain the installed directory and
the failed candidate for review; no automatic cleanup deletes recovery evidence.
Both services remain stopped after a successful switch. Start SmartDriveAccounts
and SmartDrive, then verify the health/Settings screens, permissions, previews,
native client saves, scanner ingestion and backup/recovery before reopening use.

To return to a retained payload after stopping both services:

```powershell
.\installer\windows\Update-Service.ps1 -Action Rollback -InstallRoot 'C:\Program Files\SmartDrive' -PreviousRoot 'C:\Program Files\SmartDrive.previous-<printed-id>' -MaintenanceWindow -WhatIf
```

Run without `-WhatIf` after reviewing the exact retained path. Rollback verifies
its code manifest and copies the CURRENT protected settings/account journals
into that payload before switching; it never restores older service credentials
or scanner ownership state. A missing current authority stops the operation for
review. The displaced newer payload is retained too. Confirm configuration/state
compatibility before rollback across future schema versions; code rollback is
not a data/schema downgrade. Keep all retained directories and maintenance
journals protected and out of Git. Installed service/SCM/reboot behavior still
requires VM acceptance. Developer fixture check:
`powershell.exe -NoProfile -File SmartDrive/test/Service-PayloadSwitch.ps1`.

## Pilot storage and migration

For a new company installation, copy `config.setup.example.json`, set its
installation name, stores and HTTPS origins, then generate the concrete config:

```powershell
node tools/configure-installation.mjs company-setup.json installation.json
.\tools\Install-ManagementShortcut.ps1 -ConfigPath installation.json -WhatIf
```

One installation name derives the share, Windows hostname default, store paths,
private enrollment/scan/backup/recovery paths and Office cache. Scanner intake
paths follow the same root. `displayName` can override the visible name without
moving data. The hostname may be explicitly set; review its generated 15-character
default for LAN uniqueness. OneDrive setup accepts IDs, with its secret configured
separately. The generator creates a new file exclusively and never provisions or
moves storage. Pass the reviewed generated file to the existing share/service
installers. Install the shortcut without `-WhatIf` when ready; use `-Guest` inside
the server VM to start its local service before opening management.

Changing an existing installation name requires a planned folder/share/config
migration and workstation mapping updates. Generating a new config does not
rename a live file tree or alter enrolled computers. For a label-only change,
keep `installationName` and paths stable and change `displayName`.

Use the maintenance rebranding workflow for an installed server. First review
the path plan; this command does not need elevation and makes no changes:

```powershell
.\tools\Rebrand-Installation.ps1 -ConfigPath 'C:\ProgramData\SmartDrive\installation.json' -InstallationName ExampleDrive -DisplayName 'Example Shared Files' -PlanOnly
```

After verifying backup/recovery, freeze staff file access and close open files.
In an administrator PowerShell inside the guest, stop SmartDrive and
SmartDriveAccounts, then inspect the native preflight with `-WhatIf`:

```powershell
.\tools\Rebrand-Installation.ps1 -ConfigPath 'C:\ProgramData\SmartDrive\installation.json' -InstallationName ExampleDrive -DisplayName 'Example Shared Files' -InstallRoot 'C:\Program Files\SmartDrive' -JournalRoot 'C:\ProgramData\SmartDrive\Rebrand-01' -MaintenanceWindow -WhatIf
```

Run without `-WhatIf` after review. JournalRoot must be new, outside both data
roots, with an existing parent; the tool restricts it to SYSTEM/administrators
before saving the original config, account policy, share permissions and phase
journal. It verifies the installed service config, stopped services, no open SMB
files, expected share paths and absence of linked directories. Extra shares
under the data tree require separate review. The same-parent directory rename
retains file bytes, ACLs and identity streams. Share access is recreated and
checked against its original entries. Configured store/private/cache/intake and
in-root catalog paths are rebased together; the account policy and dynamic
scanner intake paths follow the new root. The service display name updates.
Services remain stopped for verification. On failure they remain stopped with
phase requires-review; inspect actual paths and the saved originals instead of
rerunning or attempting a blind rollback.

The old staff share remains a compatibility alias to the same Stores tree while
workstations are updated. ScanIntake keeps its copier destination name. The
Windows hostname, HTTPS origin, employee/store assignments, account passwords,
template identities, audit history and OneDrive destination IDs remain stable.
This avoids requiring another employee/drive login or changing submission links.
Changing DNS, the VM hostname or the remote OneDrive folder's display name is a
separate infrastructure operation; do not replace those identities by searching
and replacing company names in state files.

Start SmartDriveAccounts and SmartDrive for acceptance after inspecting the new
config and shares. The protected journal includes workstation-branding.json,
which contains only the server/share names, display name and management URL.
Provide that file and the current trusted tools to each shared shop Windows
profile, then run:

```powershell
.\tools\Update-WorkstationBranding.ps1 -SettingsPath workstation-branding.json -WhatIf
```

Close files/Explorer windows using the old drive and run without `-WhatIf`. The
helper checks the new share with the existing saved credential, replaces matching
persistent drive-letter mappings, and restores an old mapping if its replacement
fails. It updates/renames a management shortcut owned by these tools or creates
the new one. It does not expose or replace workstation passwords. Verify reboot
reconnect, employee handoff, previews, copier intake and backup/restore. Keep the
compatibility alias until every workstation is updated; then an administrator can
remove only that old SMB share after checking for open files. Do not delete the
Stores tree, which both names share. Retain the private journal/configuration
backups and keep them out of Git. Native share/service/mapping acceptance remains
required before treating this workflow as production verified.

Folder backup capture isolates entry failures: a linked, unavailable or changing
file does not prevent its healthy siblings from being queued. The pass still
reports failure and retries the affected entry on the next run. Root/folder
access checks remain enforced, and linked files are never followed. Error
aggregation retains at most 20 exception details while counting all failures.

The manager overview includes live Office preview-worker state, active/queued
conversion counts, completed/failed totals since service start, and the last
conversion timestamp/outcome. An idle worker has no active conversion; it is
not a startup rendering self-test. A failed conversion shows needs-attention
until a later conversion succeeds. Metrics contain no filenames or source
paths. PDF output is checked before cache publication so malformed converter
output can be retried rather than permanently poisoning that revision's cache.

For recovery after loss of local backup metadata, provision the replacement
server's protected state/recovery directories and configured store IDs, restore
its OneDrive configuration, set `SMARTDRIVE_ONEDRIVE_CLIENT_SECRET` securely, and
keep the SmartDrive service stopped. From SmartDrive, run:

```powershell
node tools/recover-backup-catalog.mjs 'C:\ProgramData\SmartDrive\config.json' --service-stopped
```

The tool discovers version manifests in the dedicated OneDrive backup folder,
downloads each candidate version and verifies size/SHA-256 before publishing its
local catalog record. It rejects unknown stores, unsafe paths and conflicts with
existing local identities. Already verified records are retained, so an
interrupted rebuild can be rerun. Successfully recovered records remain if a
later candidate fails. Review the reported error before proceeding. Recovery
does not write into staff folders; after restarting the service, use the normal
manager restore operation to download selected versions into Recovery. This
rebuilds file-version metadata, not employee permissions, machine enrollment,
SMB ACLs or the migration catalog; retain independent configuration backups.

`minimumFreeBytes` defaults to 5 GiB. Backup capture, verification downloads,
restores and Office preview work check available capacity before allocating
their expected file space. This is a preflight reserve rather than an NTFS quota;
simultaneous SMB writes can still consume free space. The management overview
flags store volumes below the reserve. Pending uploads keep their immutable
snapshots through outages. After remote download/hash verification and a durable
verified version record, the local upload snapshot is removed. Remote versions
and local version records remain, and restoration downloads into Recovery.
Independently back up the protected configuration and version catalog.

Office preview cache capacity defaults to 1 GiB and can be changed with
`office.maxCacheBytes` (minimum 32 MiB). Conversion removes least recently used
revision PDFs when needed, preserving files being read or generated. Other files
in the cache directory are left alone. At most two document captures can be in
flight, with conversion serialized. Temporary input/output workspaces require
additional space beyond the PDF cache budget. Local backup snapshots also occupy
the pilot volume; the preview budget does not cap the whole data volume.

OneDrive upload sessions are durably recorded under the protected backup state
directory's `Uploads` folder. After a restart or connection failure, the worker
queries the session's missing range before sending more snapshot bytes. Expired
sessions are replaced. Once OneDrive acknowledges the complete file, retries
can finish the recovery manifest without retransmitting the file. Session URLs
are transfer credentials and are kept out of management version responses and
off-site recovery manifests. Full downloaded SHA-256 verification still controls
whether a version becomes verified. Live company OneDrive outage/restart testing
remains an acceptance step.

See [STORAGE-SETUP.md](STORAGE-SETUP.md) for this computer's checked capacity,
enclosure requirements, pilot setup and the later mirror migration checklist.
`tools/Get-StorageReadiness.ps1` provides a read-only readiness report.

Keep the Hyper-V guest operating system on a separate disk from SmartDrive
data. The host's Toshiba USB drive is currently mounted at `D:`. Start with
a dynamically expanding VHDX capped at 300 GB. Attach it to the DeckersDrive
guest as its data disk, initialize it as GPT and NTFS inside the guest, and
assign a stable guest drive letter. Point `dataParent` at that guest volume.

```powershell
.\tools\Manage-Storage.ps1 -Action Plan
.\tools\Manage-Storage.ps1 -Action CreatePilot -WhatIf
.\tools\Manage-Storage.ps1 -Action CreatePilot
```

The Toshiba disk is the only physical copy during the pilot. Configure
independent backups before copying live files into it.

For two compatible 4 TB DVR disks, test each disk and create a two-way
Storage Spaces mirror on the Windows host. Give the mirror its own drive
letter. Back up the guest data, stop writes, and shut down the guest cleanly.
The migration command checks the mirror, verifies the copied VHDX by hash,
and switches the existing VM attachment. It keeps the pilot VHDX for rollback.

```powershell
.\tools\Manage-Storage.ps1 -Action Migrate -MirrorPath 'M:\SmartDrive\DeckersDrive-data.vhdx' -WhatIf
.\tools\Manage-Storage.ps1 -Action Migrate -MirrorPath 'M:\SmartDrive\DeckersDrive-data.vhdx'
```

While the guest is off, increase the copied disk's virtual size to fit the
measured mirror capacity with headroom. Start the guest and extend its existing
data partition. Check the share, scan intake, and backup jobs before reopening
writes. The guest keeps its drive letter, SMB share name, and file paths. If
validation fails before writes resume, shut the guest down and reattach the
original VHDX at the same controller slot.

## Windows service installation

The installer validates required payload files, store IDs, Office converter,
Node runtime APIs and bridge-token format before creating application directories
or preview accounts. `installer/windows/Copy-AppPayload.ps1` supplies the
installed layout: package metadata, app/web source, operational tools, Windows
installer/migration helpers and setup examples. It rejects a nonempty destination
and supports `-ValidateOnly` without creating directories. Machine-specific
installation JSON is copied separately into protected service state; development
database test tooling is excluded from the installed tools.

The Windows payload test copies this layout into a temporary directory, runs its
configuration generator, starts the copied app with synthetic loopback settings,
checks branding/static assets and stops it. This verifies the copied app layout;
service identities, native workers, SMB and reboot behavior still require the
installed guest acceptance checks.

Inside the guest, run `installer/windows/Install-Service.ps1` as an
administrator with the installation JSON path and an installed Node runtime.
Use a Node installation readable by LocalService. The installer compiles the
Windows service wrapper, copies the app, protects service configuration and
credentials, and registers automatic startup and restart recovery.

The service runs as LocalService. Grant that identity read access to the staff
roots and modify access to the private scanner, backup, recovery, and preview
directories it needs. Grant write access to the published scanner folders.
The installer leaves the service stopped for configuration review. Start it
with `Start-Service SmartDrive` after the guest paths and HTTPS ingress are
ready. Service logs rotate at 10 MiB. Installation, service restart, and the
desktop shortcut still require live guest verification.

## Store shares and Canon intake

Scanner setup records accept optional `displayName`, `model` and
`reservedAddress` fields. The address must be an IP address, and records remain
scoped to the manager's assigned stores. The Scanners tab derives SMB2/3 server,
share, folder, local-account username and published-file paths from configured
values, with copyable text and a one-page PDF test procedure. No password is
stored in setup metadata. These fields document the approved copier setup;
they do not configure DHCP, broaden firewall rules, or prove a copier can send.

The manager Scanners tab can disable or enable a configured copier account or rotate its
password. These operations require Manage SmartDrive, a write-authorized employee
session, assigned-store access and working audit storage. Passwords are generated
on the server, returned once, and cleared from the dialog on close/sign-out;
neither credential state nor audit events store them. Update the copier after a
rotation. Rotating an already disabled account keeps it disabled. Interrupted
operations are marked requires-review and can be retried.

Initial and dashboard-created copier accounts use non-expiring passwords;
managers rotate them explicitly and update the copier. Initial setup bounds the
Windows account description to its supported length. Service installation checks
that the configured Defender MpCmdRun.exe exists before creating installation
directories or accounts, including installations that will add their first
copier later through the dashboard.

Overview probes the Windows account service using a read-only named-pipe health
request. Checks have a deadline, coalesce simultaneous requests and cache the
result briefly; the UI shows ready/unavailable and the check time. Internal
exception text, account names and passwords are excluded. The existing manager
permission protects this status. An unavailable account service means enrollment,
revocation and copier credential actions need attention even when file browsing
and the web service remain reachable. This probe verifies broker reachability;
actual account creation, SMB connections and reboot recovery remain separate
installed-VM acceptance checks.

Overview also reports assigned-store backup selections, verified/pending/retry
counts and the latest verified timestamp. A catalog read failure is shown as
unavailable rather than a healthy empty backup. Settings shows the effective
company/installation name, server/share, management URL, assigned stores and
OneDrive destination IDs/retention/restore behavior. It uses a strict response
whitelist: OAuth credentials, server environment, private storage paths and
unassigned-store details are not returned. Both endpoints require Manage SmartDrive.

Workstations shows assigned stores, enrollment state, current observed SMB
connection state and last observed drive activity. The broker uses bounded
Windows NetSessionEnum level-10 reads and returns only requested sdpc account
identities and activity timestamps, never client addresses, file paths or other
users' sessions. The service observes every 30 seconds and on manager refresh,
serializes updates with enrollment/revocation, and persists the latest observed
timestamp. Broker failure is shown as unavailable and retains that timestamp;
disconnect is not reported as revocation. This measures observed SMB activity,
not employee attendance or complete workstation usage. No client agent or extra
login is installed. Session observation and reboot behavior still need live VM
and two-workstation acceptance. API reference:
https://learn.microsoft.com/en-us/windows/win32/api/lmshare/nf-lmshare-netsessionenum

The privileged broker only accepts scanner accounts in its protected installed
allowlist, validates password/disable-state inputs, changes the password/account
flags and closes existing SMB sessions. This is separate from pausing intake
processing. Native account/session behavior still needs guest acceptance testing.
Enable restores access using the current password; it does not generate a new
credential.

The broker now has a typed scanner-create operation and a fixed provisioning
helper for dashboard enrollment. It accepts scanner IDs/accounts and
configured store IDs, derives directories from protected installation policy,
and journals account creation phases without passwords. Dynamic scanner accounts
carry an ownership tag; the broker checks it before authorizing later operations
and disables owned incomplete accounts on restart. The helper does not broaden
firewall rules. In Scanners, choose **Add a copier**, enter a unique machine ID,
display name, model, assigned store and optional reserved IP. The server generates
a dedicated account and a one-time password; copy it into the copier's address
book using the displayed SMB2/3 instructions. Successful enrollment joins the
running intake worker, management controls and credential actions immediately,
and survives service restart. The native setup requires the initialized store
and Scans folders created by Initialize-Shares.ps1.

The private enrolled-scanners.json registry is saved before account creation and
contains no passwords. Ambiguous failures trigger a best-effort account disable
and remain visible as requiring administrator review; the service never retries
account creation or adopts an existing folder/account automatically. Review both
this registry and the protected scanner-accounts.json broker journal after a
partial setup. Retain both files in server configuration recovery and when
updating the installed service. Native creation, ACLs, SMB sessions and an actual
copier scan remain live VM checks.

Use the example configuration as a layout template and replace the example
HTTPS hostnames with the actual approved endpoints. Store roots are under
`<dataRoot>/Stores/<location ID>`. The staff share exposes Stores with
access-based enumeration. Staff see only the stores assigned to their
workstation. Each store contains Studio Jobs and Scans.

Run `installer/windows/Initialize-Shares.ps1` inside the guest with the config
path and the shop's allowed client network ranges. Supply a hashtable mapping
each scanner ID to a SecureString password. The tool creates new store groups,
private service state, the staff share, scanner accounts and intake share,
and private-network firewall rules for SMB and Windows discovery.

The tool refuses an existing data root or conflicting accounts and shares.
Add workstation accounts to the appropriate `SDStore<location ID>` groups.
On the Canon, set the destination to the guest's ScanIntake share and the
Canon-C5040 subfolder using its dedicated account. The service publishes
accepted scans into the assigned store's Scans folder. Test delivery and
store isolation before moving the library.

## Legacy library export

Run `node tools/export-studio.mjs <legacy-studio-templates> <new-export-folder> --dry-run`
to inventory the badge, business card, label, and bill book libraries and verify
their current revision hashes. Remove `--dry-run` to copy verified files into
a new export directory. Freeze legacy writes for the final export.

The export manifest preserves template IDs, original names, storage stores,
owners, shared-store assignments, and revision metadata. It is written only
after the files are verified. An incomplete export must not be used for
cutover. The tool preserves the legacy tree. Importing the staged files,
connecting submission links, and switching the SmartStudio list remain
separate migration steps.

## Workstation access tools

Inside the guest, an administrator can run
`installer/windows/New-WorkstationAccount.ps1` with an account such as
`sdpc_studio01`, the assigned store IDs, and a SecureString password.
The tool grants only the matching SDStore group memberships. To revoke it,
run `Revoke-WorkstationAccount.ps1`, which disables the account and closes
its active SMB sessions.

On the staff computer, sign into the shared shop Windows profile and run
`tools/Connect-Workstation.ps1` with the dedicated server credential. Use
`Get-Credential` to enter it interactively. The helper saves it through the
Windows Credential Manager API. An optional DriveLetter creates a persistent
mapping. The normal workflow opens the share through File Explorer Network.
The credential persists for this Windows profile across logons. Use
`ReplaceCredential` when rotating a previously saved server credential.

The Workstations section can issue a code for a named workstation and assigned
store. Run Enroll-Workstation.ps1 on the staff computer with the public HTTPS
origin, then enter the one-time code. The code expires after 15 minutes. The
helper saves the returned credential through Windows Credential Manager.

The guest installer adds a separate SmartDriveAccounts service. It accepts
only workstation creation into configured store groups and revocation through
a local named pipe restricted to LocalService, administrators and SYSTEM.
It rejects network pipe clients. The web app continues under LocalService.
Installation, update, repair and rollback configure automatic restart recovery for both services:
10 seconds, 30 seconds, then 60 seconds, with the failure counter reset after
one day. This requires installed-service crash/reboot verification in the guest;
maintenance applies the policy before activating candidate code. A policy failure
leaves the current code in place and both services stopped. Successfully applied
recovery settings persist if a later payload activation fails.
Revocation disables the account and closes its active SMB sessions. Account
service startup, enrollment, and revocation still need live VM verification.

## Remote HTTPS gateway

The Worker in `cloudflare` forwards only the SmartDrive asset, file, account,
and management routes. Set PUBLIC_ORIGIN to the chosen staff hostname and
DRIVE_ORIGIN to the guest's tunnel hostname. Protect the origin hostname with
a Cloudflare Access Service Auth policy. Store its credentials as Worker
secrets DRIVE_ACCESS_CLIENT_ID and DRIVE_ACCESS_CLIENT_SECRET. Route only
the public staff hostname to this Worker.

The gateway checks browser origin on writes, bounds request bodies, forwards
employee cookies and CSRF tokens, and disables caching. Origin service tokens
are supplied by the Worker. Only employee session cookies are returned to the
browser. Verify the public hostname from cellular data and direct-origin access
before live rollout. This source configuration has not been published.

Cloudflare service authentication reference
https://developers.cloudflare.com/cloudflare-one/access-controls/service-credentials/service-tokens/

## Explorer previews

SmartStudio's Windows packaging now compiles and registers the thumbnail and
Preview pane handlers for .sspub and .sstbadge. They read only the embedded
preview image through Windows stream initialization. They bound metadata,
image dimensions, and decoded size and reject files without an embedded
preview. Save older files in updated SmartStudio to add a preview.

The shell extraction smoke test covers both native formats. Installed Windows
thumbnail caching, Preview pane rendering, and refresh after a network save
remain live verification steps.

## Word and PowerPoint web previews

Install LibreOffice in the guest and configure office.converter with its
soffice.bin path and office.cacheRoot with a private cache folder. The service
installer builds OfficePreview.exe and blocks outbound networking for the
converter program through Windows Firewall.

The app copies a bounded .docx or .pptx snapshot into a private conversion job,
keys cached PDFs by the captured content hash, and cleans temporary jobs.
The installer creates a dedicated sdpreview account whose generated credential
is held in the protected service settings. The Windows runner gives that account
access to the current conversion workspace. Store data and service state remain
protected by their service and store ACLs. The runner starts the converter suspended, places it in a job limited
to one process and 512 MiB total memory, then resumes it with a clean environment
and a private profile with macro security set to Very High. It enforces a total
60-second deadline and terminates the job on exit. The initial profile restart
requested by LibreOffice is handled once within that deadline.

Fresh-profile conversion of known local Word and PowerPoint documents was
verified before enabling the dedicated-account boundary. The dedicated-account
launch, guest firewall isolation, LibreOffice rendering fidelity, and phone PDF
viewing still require acceptance checks.

## Canonical library import

SmartStudio can be updated before an older SmartStock server: an HTTP 404
confirming that the optional drive-catalog endpoint is absent keeps the legacy
chooser available. The client preserves HTTP status when parsing errors so
authentication failures, revoked devices, malformed successful responses and
server outages cannot trigger this fallback. A configured SmartDrive outage
continues to fail rather than switching back to legacy library authority.

After cutover, Save on a new badge or publication opens a native save dialog
instead of attempting a legacy server upload. After loading the SmartDrive
catalog, the dialog starts at the configured staff share; browse into the owning
store's Studio Jobs folder to save the new design. The share name comes from the
server's company configuration. Existing native documents keep their save path.
An already-open legacy cached badge must be reopened from SmartDrive or saved
as a new copy; it cannot be reported as saved back to the shared library.

Native SmartStudio Save As operations capture the destination revision before
replacement approval. A destination observed as absent must still be absent
under its save lock; an approved replacement must still match the observed
revision. Badge and publication copies use the same save locking as normal
saves. Concurrent creation or modification rejects the save and keeps the
in-memory design available for another name. Automated tests exercise this
contract; the installed two-workstation SMB acceptance check remains required.

When SmartStock's SmartDrive bridge is configured, existing customer badge
submission links retain their database template IDs, link tokens, access codes
and queue records. SmartStock first authorizes the enabled link in its local
database, then reads the current badge revision from the server-only
`/internal/studio/template` bridge. Customer responses still contain the form
schema rather than the raw design or SMB path. Employee queue/print-record
operations use the employee's current store scope. The bridge preserves legacy
storage IDs even when a design belongs to a different store, and SmartStock
verifies the returned content hash and requested identity. A SmartDrive outage
fails the request rather than serving stale legacy fields.

The published file's Windows identity stream lets native file queue/record
actions work before the first resave. The staff client can route those actions
without a legacy cache sidecar and embeds the identity on the next save.
Existing codes/links and queued submissions must still
be verified against a copied database and library before production cutover.
Automated database queue tests require the integration database and can be
skipped when it is unavailable; a green unit run does not prove live migration.

On Windows with PostgreSQL 17 tools installed, run
`tools/Test-SubmissionDatabase.ps1` to execute these tests without a live store
database. It creates a unique temporary cluster, binds only 127.0.0.1:55467,
creates the explicitly named disposable database, and verifies its data directory
before Maven runs. It requires a fresh test report with no skipped cases, then
stops and removes the successful cluster. Failed-run evidence is retained in the
reported temporary directory, with a shutdown attempt in all cases. The suite
includes preserving link tokens, access codes, sessions and pending submissions
when the template reader changes to the current identity-bearing design, plus
rollback when that source is unavailable. These are synthetic integration checks;
copied-library/database migration and installed-client acceptance remain separate.

SmartStudio documents support optional `smartDrive` metadata containing the
original template ID, legacy storage store ID and category. Opening a migrated
design through the new picker attaches that identity in memory; the next save
embeds it in the badge JSON or publication JSON. Existing files remain readable
without the field. Document copies and undo snapshots preserve it. This provides
the portable identity used by the file index and rename resolver. On catalog or
identity lookup, the bounded index scans authorized Studio Jobs folders and
checks metadata against current file handles. It finds identified files after
renames and service restarts. Duplicate identities are omitted from the desktop
catalog and cannot resolve a link. Files without identity metadata remain
available through ordinary file browsing. The Windows publisher attaches identity
in the file's SmartDriveIdentity NTFS stream without changing the design bytes
or content hash. This protects the first rename before an embedded-identity save.
SmartStudio reads the stream on direct Explorer opens and embeds the identity
on its next save. Backup manifests retain the stream identity and restore
reapplies it. Software that strips Windows streams can remove this protection
from unresaved designs; use the publisher and verified backup/restore workflow.
Customer submission-link
source integration is implemented but still requires live cutover verification.

The desktop catalog bridge uses a server-only 32-byte random lowercase hex
`SMARTDRIVE_STUDIO_BRIDGE_TOKEN`, configured securely on SmartDrive and the
SmartStock server. The Windows service installer preserves the value from its
process environment in the protected service settings. SmartStock additionally
needs `SMARTDRIVE_STUDIO_ORIGIN`, a dedicated HTTPS origin reaching SmartDrive's
`/internal/studio/catalog`. If Cloudflare Access protects that origin, configure
`SMARTDRIVE_ACCESS_CLIENT_ID` and `SMARTDRIVE_ACCESS_CLIENT_SECRET` on SmartStock.
The public web gateway does not expose this internal route. Keep all bridge and
Access credentials off workstations.

The authenticated `/v1/studio/drive-catalog` LAN endpoint checks device approval
and the employee session, loads that employee's current store assignments from
SmartStock, and queries the bridge. Employee-supplied store scopes are ignored.
The bridge filters migrated designs and returns their native SMB paths. Without
the SmartStock origin setting it reports disabled. With it configured, an outage
fails the catalog request rather than quietly switching library authority.
Updated SmartStudio clients query this endpoint when opening the Badge Cards,
Business Cards, Labels or Bill Books template chooser. When enabled, a searchable
SmartDrive list replaces the legacy chooser and opens the selected SMB file
directly. Existing native-file saves use their original paths and revision locks.
New files can be saved under Studio Jobs on the standard staff share through
File → Save As. SmartStudio generates a new identity for copies saved through
UNC paths or the standard mapped drive layout, where the first path component
is the store ID. Those identified files appear in the next catalog query.
Renaming keeps the existing identity. Exports to ordinary local folders clear
the drive identity. Copies made in Explorer retain embedded metadata and are
treated as ambiguous until their identities are repaired.

Configuring the SmartStock bridge also blocks legacy design mutations on the
server. Updated clients stop legacy downloads and automatic legacy uploads after
receiving the cutover flag. Older clients receive a read-only/cutover response
when attempting a legacy save. Legacy reads, submission queues and print records
remain available for recovery. Updated customer and staff queue reads use the
SmartDrive template bridge while retaining their legacy database identities. Do not
enable production cutover until customer-link resolution, native access and
recovery have passed acceptance checks.

SmartStudio local/SMB document renames now acquire the same persistent sidecar
locks used by saves, covering both source and destination names. A changed
revision or a competing SmartStudio save stops the rename before replacement.
Automated checks cover both name locks, stale revisions and preserving the
source after failed content generation. Cross-workstation SMB locking and drive
disconnection behavior still require the two-client acceptance check.

In SmartStudio, choose Account → Role permissions. The existing role editor
opens after the server checks `ROLE_MANAGEMENT`. Select the employee's role,
enable **Manage SmartDrive** under Administration → SmartDrive in the Desktop
tab, and save. This grants management access to everyone in that role; ordinary
employees retain browse/preview access without this grant. Each management
request checks current employee permissions on the server. The role window
closes on SmartStudio sign-out, session loss or application close. The SmartStock
database permission migration must be applied before the new permission appears.

Run `node tools/import-studio.mjs <verified-export> <new-import-folder> <store-id> [...]`
to stage the canonical library. The importer verifies every exported file,
requires all owning and shared stores to be configured, and keeps one physical
copy of each design. Imported paths group designs by category and store access,
with the original name and ID retained. The catalog maps legacy IDs and storage
stores to the new paths.

Keep catalog.json in private service storage and set migrationCatalog to that
path after approved publication. The browser uses its shared-folder assignments
to show shared designs under their owner store while filtering unrelated files.
Backup selection for a shared folder belongs to its owner store.

The authenticated GET `/employees/drive/api/template?kind=BADGE&store=1&template=<legacy-id>`
resolves an imported legacy identity to the same opaque file ID used by browsing.
`store` is the legacy storage store, which can differ from the current owner.
Resolution checks the employee's current store access and the current file path;
it does not return physical server paths. Missing and unauthorized designs both
return an unavailable response. This endpoint supplies the identity mapping for
employee preview-link integration. Customer submission forms use the separate
server-only template-read bridge rather than this employee endpoint.

### Rehearsing a local legacy library

Use the protected rehearsal wrapper on a trusted Windows workstation with a
current built/installed SmartStudio renderer. Choose a new WorkingRoot outside
the repository and live library. Its existing parent must have no junctions or
symlinks. The wrapper restricts the working folder to the operator, SYSTEM and
administrators before copying any designs:

```powershell
.\tools\Rehearse-LibraryMigration.ps1 -SourcePath 'C:\Users\operator\.smartstock\profiles\production\studio-templates' -WorkingRoot 'D:\PrivateRehearsals\SmartDrive-01' -JavaPath 'C:\Users\operator\AppData\Local\Programs\SmartStudio\runtime\bin\java.exe' -ClassPath 'C:\Users\operator\AppData\Local\Programs\SmartStudio\app\smartstudio-<version>.jar;C:\Users\operator\AppData\Local\Programs\SmartStudio\app\lib\*'
```

It exports and imports verified copies, stamps copied NTFS identity streams,
prepares copied legacy previews with original-file recovery, verifies native
catalog/store assignments and authorized/unauthorized web previews, checks a
representative badge's file-backed link resolution, and verifies one local-test
backup/restore per design kind. It compares a final source fingerprint with the
exported metadata/revisions. A changing live source or a failed check stops
successful completion and retains private evidence; freeze writes for actual
cutover. The local transport fixture is not company OneDrive verification, and
the file-backed badge check does not validate real customer-link database tokens.
No source files, live database, shares, accounts or service configuration are
modified. Production catalogs, journals, copied files and reports stay outside
Git.

A retained copy that finished every preview preparation/delivery check can be
reverified with `-VerifyPreparedCopy` after correcting a later rehearsal-check
failure. It saves the earlier report, validates that the live source fingerprint
still matches, rebuilds a checksum-verified import reference, compares catalog
records/sharing, repeats access/preview checks and uses fresh local-test backup
and recovery directories. It does not rerender or overwrite the retained copied
designs. Earlier evidence and recovery files remain available.

For employee preview links, open the web app with
`?kind=BADGE&store=1&template=<legacy-id>`. After employee sign-in, the app resolves
the identity and opens its preview. The server still checks store access on
resolution and on each preview request. These employee preview links are
separate from the customer-facing submission links.

In the guest, use the publication helper during a maintenance window, with
legacy writes and staff share writes frozen. The configured store roots and
SDStore groups must already exist. Keep the staged import outside staff shares:

```powershell
.\installer\windows\Publish-StudioImport.ps1 -ImportRoot 'D:\Migration\Imported' -ConfigPath 'C:\ProgramData\SmartDrive\config.json' -CatalogPath 'D:\Shares\DeckersDrive\Private\catalog.json' -MaintenanceWindow -WhatIf
```

Review the dry run, then run the same command without `-WhatIf`. It verifies
source hashes, applies the matching NTFS store-group ACLs and parent traversal
permissions, copies without overwriting, verifies destination hashes, and
atomically publishes the private catalog last. Existing import folders or an
existing catalog stop the operation for review. A failed run can leave partial
folders; keep writes frozen and inspect them before any retry. Set
`migrationCatalog` in the service configuration only after publication succeeds.
Test store access through both SMB and the web before enabling staff writes.

For migrated files without embedded previews, keep the maintenance window open
after publication, stop SmartDrive, and run the preview utility using a current
SmartStudio installation containing StudioPreviewPreparation:

```powershell
.\tools\Prepare-LegacyPreviews.ps1 -ConfigPath 'C:\ProgramData\SmartDrive\config.json' -RecoveryRoot 'D:\Shares\DeckersDrive\Private\LegacyPreviewRecovery-01' -StudioPath 'C:\Users\operator\AppData\Local\Programs\SmartStudio' -MaintenanceWindow
```

The configured migrationCatalog selects only published Studio Jobs records.
RecoveryRoot must be a new private folder outside every staff store root; its
parent must already exist. The tool verifies a recovery copy against the
original migration SHA-256 before replacing a preview-less file. It preserves
unknown design metadata, archive asset bytes and migrated logical identity,
embeds a badge/first-page PNG, and records before/after hashes in
preparation.jsonl. This is an intentional new file revision after the unchanged
import was verified; the original export, catalog hashes and recovery copies
remain available. Existing valid previews are skipped. Paths with links,
unexpected unprepared revisions and duplicate targets are rejected. Changed
source files are not replaced. Failures retain originals/recovery evidence and
stop successful completion of the command; keep writes frozen and review the
journal before retrying with a new recovery folder. The tool never edits the
legacy library. Restrict recovery-folder access and retain it with configuration
backups; production journals and recovery files must stay out of Git.

Restart SmartDrive and verify thumbnails/Preview pane on installed Windows
clients, web previews, current content hashes and existing submission links
before reopening file access. Preview rendering requires the same fonts as the
design workstations; visual review remains part of migration acceptance.

Developer validation: run `mvn -f SmartStudio/pom.xml package`, the SmartDrive
Node tests, and `node SmartDrive/test/preview-cli-smoke.mjs`. The smoke check uses
only temporary synthetic files and exercises the actual Node CLI and built Java
renderer, including verified recovery and an already-prepared second run.

Run `powershell.exe -NoProfile -File test/Publish-ImportPaths.ps1` to verify the
publisher's path checks, including junctions in file ancestors.
Live submission-link resolution, the SmartStudio list cutover, and the library
transfer still require migration acceptance checks before production use.
