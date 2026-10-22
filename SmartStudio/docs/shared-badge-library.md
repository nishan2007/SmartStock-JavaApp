# Shared badge-template library

## Behavior

The authenticated store server is the authority for reusable `.sstbadge` designs.
Templates are scoped to the signed-in store, stored under the active server
profile's `studio-templates/store-<location>/`, and served through HTTPS LAN API
`POST /v1/studio/templates`. Every operation checks the device, staff session and
Allow Studio approval. Saving additionally requires Company Preferences or
Manage Custom Order Items permission. Registers never receive OneDrive secrets.

SmartStudio 0.1.25 and later switches to the shared library after sign-in and
automatically migrates the computer's previously configured local badge-template
folder once. The default is Documents/SmartStudio/Badge Templates when no folder
was selected previously. Migration runs in the background after an online refresh,
using the signed-in store and the server's existing save-permission checks.
Original files remain unchanged. A durable migration record is kept alongside the
endpoint/store-scoped cache. If the source folder is unavailable, the server is
offline, permission is denied, or a file is invalid, migration remains pending
and retries during later refreshes. Signing out cancels subsequent uploads.

The first migration snapshots the local filenames; after completion it does not
watch that folder or upload newly added local files automatically. Content-based
identities and existing server digests prevent duplicate imports across retries
and computers. An edited server head is never overwritten by migration, including
when an earlier upload response was lost. Completed files are checkpointed so a
restart resumes pending files. Session photos and personal card details are not
part of the portable template upload.

For another folder or designs added after migration, use Template > Import
template folder explicitly. That older manual workflow creates fresh identities;
avoid repeatedly importing the same folder through it. Migrated templates then
use ordinary shared-library saves, refreshes and revision-conflict protection.

Every computer downloads designs into a cache scoped to the server endpoint and
store. Opening/searching cached templates does not wait for the server. The
library refreshes in the background every 30 seconds; Refresh requests an earlier
check. The chooser updates while open. Only missing or hash-changed files are
downloaded, with up to four concurrent requests. Downloads are validated as badge
templates and against SHA-256 before atomic replacement. Network operations run
off the Swing event thread. This does not guarantee saturation of a particular
LAN link; actual throughput depends on the server, client disks, TLS, Wi-Fi and
network hardware and needs a multi-computer benchmark.

The current open design is not silently reloaded by refresh. Save submits its
original revision hash. If another computer saved a newer revision, the server
rejects the stale save. Reopen the latest design before saving, or create a new
template through an explicit separate workflow. Server revisions are immutable
and the current head is atomically replaced after the revision and backup queue
entry exist. No database migration is required.

## Offline mode

Work offline is available from connection and sign-in screens. Previously cached
badge templates can be opened, edited, framed, previewed and printed. Offline
edits stay in memory: Save template and template export refuse to write. No
offline upload queue is created. New computers with no cache cannot obtain
server templates while disconnected. The shared-library status explicitly shows
offline/server unavailable. A failed refresh preserves existing cache files.

Reconnect, sign in if necessary, and Refresh to return online. There is no silent
save of offline edits. If the same design remains open, it can be saved explicitly
after reconnecting, subject to the revision conflict check. Closing/discarding an
offline edit loses those changes. Personal photos and entered card details remain
session data and are not part of the shared library or backup.

This feature shares badge designs. Business-card Publisher documents are not
included in this library implementation.

## OneDrive backup and recovery records

The server reuses its existing certificate-based OneDrive configuration. Each
accepted template revision is queued durably on local disk. A daemon retries
pending work every minute. It uploads a JSON envelope containing the template
identity, name, SHA-256, size, update time and template content through the private
file provider, downloads the uploaded object, and verifies the complete SHA-256
before marking the revision backed up. Backup never blocks LAN saves and a failed
backup leaves its queue entry in place.

After revisions are verified, a recovery catalog listing current template heads
and remote backup object IDs is also uploaded and fully download-verified. Status
distinguishes revision backups pending, recovery index pending and a backup error.
Copies of older verified revisions remain in OneDrive and on the server.

The server has a persistent `studio-templates/library-id.txt`; preserve it in
server-profile recovery. It scopes backup keys to this server library so separate
servers/profiles cannot overwrite each other's catalogs. The recovery catalog ID
is returned by LIST as `recoveryCatalogId`. The OneDrive app folder contains
`private-order-<backupId>` objects because this feature reuses the existing private
file transport. `backup-catalog.json` in each local store folder records the
verified remote revision IDs. A recovery operator can download the catalog,
then each referenced JSON envelope, verify its content SHA-256 and restore the
`.sstbadge` file. No automatic restore or destructive remote-delete endpoint is
introduced. Preserve the local profile and recovery catalog ID off the server.

Pending/failed backup status must not be treated as a completed OneDrive backup.

## Validation and rollout

Tests cover store isolation, version conflicts, path rejection, durable revision
queues, failed backup/retry with verified revision/catalog objects, cached reads,
unchanged-file transfer avoidance and refusal to save offline edits. The existing
production OneDrive configuration passed the provider's real upload/download
probe during development; template-library end-to-end LAN and real revision
backup acceptance still require deployment of both application updates.

The full isolated server run passed 1,022 tests (60 skipped); the SmartStudio run
passed 58 tests. An earlier run against the shared Maven output failed with
missing-class errors while that output was changing. Repeating in an independent
build directory passed. Security and whitespace checks also passed.

Roll out the updated SmartStock server first, then SmartStudio. Confirm the
server library and OneDrive readiness, import the existing folder once, and test
with two computers: open, edit, save, refresh, stale-save conflict, disconnect,
cached editing, rejected offline save, reconnect and explicit save. Verify a
revision and recovery catalog appear in OneDrive. Benchmark a cold cache and a
warm cache on the actual LAN; automated tests do not prove physical link speed.

Nothing in this source change moves existing files, installs an application,
publishes a release, or performs a live template-library migration.

SmartStock 1.1.37 / SmartStudio 0.1.26 extend this storage model to every template category. See [Shared template library](shared-template-library.md).
