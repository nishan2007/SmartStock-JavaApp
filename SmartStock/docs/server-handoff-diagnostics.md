# Temporary internet handoff diagnostics

Source implementation; not yet packaged, published or installed. No connection
is enabled automatically by installing or opening SmartStock.

On the physical server, sign in with Role Management permission, open Company
Preferences > Backups > Server Handoff. The service checks this permission in
the local database and requires an active user and SERVER mode.

After stopping writes for the final handoff, save the old-server baseline to a
private destination outside Git. On the replacement load that JSON, then choose
Share diagnostics (30 min). Send the generated link privately to the person
verifying the handoff. The existing bundled Cloudflare client must be available
and allowed outbound internet access; no inbound router port forwarding is used.
This uses a separate temporary tunnel and does not require enabling production
storefront/sync writers on the replacement.

The link holds a random capability in its URL fragment. The browser exchanges it
as an Authorization bearer header for an immutable report on the same origin.
Only GET /report accepts the token; no command, file browsing or mutation API is
exposed. The loopback-only HTTPS listener uses the existing usable server identity
and fails rather than creating one. The report expires in 30 minutes, allows at
most 60 authenticated reads, and has no-store/no-referrer responses. Revoke or
close the dialog to stop the listener and tunnel. Restarting the application
loses the in-memory session. Treat copied links and downloaded reports as private;
revocation cannot erase an already downloaded report.

Reports include capture time, environment/store ID, processor count, server-role
state, local TCP listener availability, public LAN certificate fingerprint,
consistent database table counts, and aggregate hashes/counts/bytes for selected
profile asset folders. Content, customer records, credentials, logs and individual
asset names are not included. Hashing large photo/model folders may take minutes.
Selected folders are image-store, custom-order-media, studio-templates,
studio-photo-queue and ai-models. Symbolic links fail capture for manual review.

Comparison identifies changed sections and differing tables/folders. Database
counts are not content hashes, file reads are not a database/filesystem atomic
snapshot, and live writes/queue expiry can cause differences. Matching automated
sections never claims complete verification. Roles, sequences, credentials,
actual authenticated LAN access, production tunnels, sync, startup after reboot,
register pairing, printing, drawers and NFC still require the handoff runbook.
Reports are snapshots: generate another connection for fresh evidence.

Drawer/register continuity now also captures aggregate SHA-256 content evidence
for drawer definitions, sessions (including OPEN state and opening cash), device
assignments, handovers, counts, the cash-activity input tables, device pairing and
credential records, employee sessions, roles, permissions and store assignments.
Employee authorization uses only user ID, role and active status. Device heartbeat
and machine-display metadata are excluded; credential values are included only
inside the aggregate digest and never exported as individual hashes or records.
The source database transaction uses a consistent snapshot and UTC timestamps.

Changed content at the same row count blocks a preservation pass, as do missing
required tables, an older baseline without continuity evidence, a changed store
or TLS identity, and missing/expired credentials on approved unblocked registers.
No credentials are extended and no drawer is closed by diagnostics. Pause all
register writes and capture a fresh final baseline before the transfer. Preserve
the full database and existing usable HTTPS private key using the handoff runbook;
this feature does not perform that migration. Windows-protected keys require
appropriate reprovisioning. On the A7, compare before taking new transactions,
then verify every original register can authenticate and see the original open
drawer session and expected balance. Employee login may expire independently
of device pairing; automatic reconnection is never claimed by the snapshot alone.

Automated tests exercise capability authorization, methods/paths, read limit,
expiry, revocation, content/name hashing and partial-evidence comparison. Real
internet connectivity, installed UI and A7 verification remain deployment checks.
