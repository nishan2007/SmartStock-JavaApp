# Windows server handoff to GEEKOM A7

Prepared 2026-10-18. This is a preparation and cutover runbook, not evidence of
a completed backup, restore, or migration. No computer has been renamed and no
live task, database, application, or network setting has been changed.

The replacement will take the current server name. The retiring computer will
become `POS-SERVER02`. Never connect both machines with the same hostname or IP.
Only one machine may run the production server, sync writer, or tunnel connectors
at a time. Renaming Windows alone does not retire SmartStock server tasks.

## Prepare the replacement

1. Set up the A7 under a temporary unique name. Install Windows updates and the
   supported AMD graphics driver. Check RAM, free disk space, and wired networking.
2. Keep it isolated from production server startup and tunnel connectors during
   preparation. Install the verified current SmartStock release using its packaged
   runtime, and PostgreSQL compatible with the source (currently PostgreSQL 17).
   Do not create a new store identity or enable production sync as a shortcut.
3. Arrange a private transfer destination outside Git and public/shared download
   folders. Encrypt removable media holding production data or keys. Confirm
   space for the database dump, profile assets, models, and a second backup copy.
4. Record the current server's DHCP reservation/static address, subnet, gateway,
   DNS records, firewall rules, service account, task settings, application version,
   certificate fingerprints and store/device identity in a private inventory.
   Keep passwords, tokens and task arguments out of the public runbook.
5. Confirm access to server credentials and tunnel administration. Windows
   current-user DPAPI secrets are tied to the original Windows account/machine;
   using the same computer/user name does not make them portable. Reprovision
   credentials on the A7 through supported setup, without displaying secrets.

## Capture and rehearse before cutover

Create a verified logical PostgreSQL backup with PostgreSQL 17 tools and an
authorized database account. Use a custom-format dump and capture required roles,
ownership, extensions and grants separately; a database dump alone does not
include cluster roles. Do not place passwords on command lines or in Git.

Restore into an isolated database with production tasks/tunnels disabled. Verify
schema, counts and recent business records, sequences, ownership and application
access. A successful `pg_restore --list` or file hash is not a restore test.
Do not copy a running PostgreSQL data directory as the migration backup.

Inventory the active Windows service/task account's `.smartstock` home, including
files outside `profiles/production`; do not assume only the interactive user's
profile is used. Transfer the following through the private backup process:

- Production profile data: `image-store`, `custom-order-media`,
  `studio-templates`, and `studio-photo-queue`, including metadata/tombstones.
  Preserve original first-print expiry timestamps; do not reset the 24-hour timer.
- `ai-models` separately from installers/update ZIPs. Verify model SHA-256 against
  the signed catalogue; retain the model-free application packaging policy.
- Application, mobile web, scheduler, hardware and backup settings, with paths
  reviewed for the new Windows account. Treat all configuration as confidential.
- Root LAN/mobile HTTPS identities, certificates and necessary private keys,
  server/device pairing material, and any relevant credential directories.
  Determine which keys are protected by Windows before expecting them to transfer.
- Tunnel configuration/credentials, scheduler configuration, shared customisation,
  and any additional asset locations referenced by configuration or the database.

Preserving the hostname does not preserve TLS trust. Verify certificate/key
usability and the original fingerprint on the replacement; if identity cannot
be retained, plan explicit register re-pairing and phone re-enrolment. Confirm
phone-pairing records and their signing/encryption dependencies before cutover.
Do not blindly overwrite the A7's newly provisioned credentials with DPAPI blobs.

## Final cutover (requires a scheduled sales pause)

1. Stop new sales, orders, edits and phone uploads; ensure registers have finished
   posting transactions. Record the last accepted transaction/time and sync state.
2. Stop and disable the old SmartStock server/sync and tunnel startup tasks after
   outstanding writes finish. Close server-side application sessions. Verify no
   remaining writers. Keep PostgreSQL available for the final consistent dump.
3. Take a fresh final database backup and final asset/config snapshot while
   writers are stopped. Generate a private SHA-256 manifest of transferred files.
   Compare source and destination hashes; retain an independent backup copy.
4. Restore the final snapshot to the A7 using the rehearsed procedure. Reconcile
   schema, records, sequences, roles and local assets before enabling production.
5. Rename the old computer to `POS-SERVER02` and restart it. Keep its production
   server/sync/tunnel tasks disabled and disconnect it during address takeover.
6. Assign the old server name to the A7 and restart. Move the DHCP reservation
   to the A7's network adapter or safely transfer the static address after the
   old adapter is offline. Verify forward name resolution from every register.
   Check custom DNS, hosts entries, public tunnel origins and certificate names.
7. Configure the A7's server task account, credentials, private-network firewall,
   and startup using supported installation tools. Enable only the replacement's
   production server and tunnel tasks. Verify local database, authenticated LAN,
   sync, storefront, phone capture and scheduled jobs. Preserve server-side roles.
8. Reboot the A7 and verify automatic startup before releasing the sales pause.

## Acceptance and rollback

Verify administrator/staff login, permissions, register pairing, latest inventory,
customers, balances, recent sales/orders and media, offline/reconnect behaviour,
phone upload and shared queue, templates, HTTPS trust, backups and restore, plus
physical printers, cash drawers and NFC on the actual registers. Check public
phone capture and storefront from outside the store network. Benchmark Best
background removal on the A7 after migration with sufficient free RAM.

Keep the original machine and backup intact until acceptance. If the replacement
has accepted no new writes, stop its tasks/tunnels, isolate it, release its name/IP,
and restore the old server's original name/address before re-enabling old tasks.
Never bring both authorities online. If the A7 has accepted writes, first capture
and reconcile those writes; switching to the old snapshot would lose transactions.

Reusing POS-SERVER02 later as a register requires configuring Register mode and
removing server-only credential access through a separately reviewed conversion.
Do not start it as a second copy of the production store server.

## Current preparation status

- Checked hostname, PostgreSQL service, relevant scheduled-task names, profile
  directory names and current source status using read-only inspection.
- Live database dump, private credential/identity export, off-device backup,
  restore rehearsal, A7 setup, name/IP changes and hardware acceptance remain
  outstanding. No migration-ready backup is claimed.
- The working tree contains extensive uncommitted work. Before development moves,
  review and intentionally checkpoint only the intended code on a branch and push
  it; use a separate clone on the A7. Do not copy secrets or sync the live checkout.
- Background-removal performance changes currently exist in source; do not assume
  the installed release on either machine includes them. Packaging/publication
  and installed-app verification remain separate from this server handoff.
