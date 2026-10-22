# SmartStudio 0.1.25 — automatic migration of existing badge templates

After updating and signing in online, SmartStudio migrates the computer's saved
legacy badge-template folder to the authenticated store server automatically.
It uses the existing folder preference or Documents/SmartStudio/Badge Templates.
It does not search arbitrary folders or upload Publisher business cards/labels.

The first migration snapshots filenames and checkpoints completed files in the
server/store-scoped client cache. Invalid files, an unavailable folder, missing
permission or a failed network call remain pending and retry on later refreshes.
Once finished, migration does not monitor later changes to the old folder.
Original files are retained. Signing out prevents subsequent uploads.

Canonical content hashes and deterministic identities avoid duplicates across
retries and computers. Existing identical server revisions are reused. A newer
server design is never overwritten. A lost upload response can be reconciled by
reading its known identity. The store server retains device/staff/Studio approval
and save-permission enforcement; no new privileged endpoint or schema is needed.
Accepted uploads enter the existing durable OneDrive revision/catalog backup
queue. Migration completion means server import, not verified cloud backup.

Six migration regressions cover resumable invalid files, permission failure,
missing folders, duplicate retry/multiple clients, lost responses, newer server
heads, existing random identities, unchanged originals and inactive sessions.
All 76 SmartStudio tests, repository security and whitespace checks passed.
The final installer was extracted; its application and shared dependency JARs
match the build inputs and its native launcher passed startup. Loose files and
nested JARs were checked to exclude model weights. A real two-computer migration and verified
OneDrive revision/catalog round trip remain live acceptance checks.

This change is prepared for the next release. Packaging does not publish or
install it on the computer that currently holds the badge templates.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Installer | 225119061 | 4d04dec7144f472c2af258cc0696d7f37a28833c8386d0951732a63dc578f8ed |
| Portable ZIP | 234096176 | cbf742c9856623e17196dcdd875f765d7bb4c72f3e3f9ac6138d40e60490f096 |
