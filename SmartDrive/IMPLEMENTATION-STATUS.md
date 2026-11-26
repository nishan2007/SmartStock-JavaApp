# SmartDrive implementation and acceptance status

Reviewed against the agreed plan and implementation-report source on 9 November
2026. This is an incomplete implementation, not a production acceptance record.
Source tests do not establish installed Windows or physical-device behavior.

Local production-library rehearsal completed on 8 November: 201 designs from the
one store present here (87 badges, 72 business cards, 42 labels; 160,300,033
original bytes). Export/import hashes and identities, 201 authorized web previews,
unauthorized-preview denial, catalog assignments and three local-transport sample
restores passed. A representative badge resolved through the file-backed link
bridge. Source metadata/revision fingerprint matched afterward; no live cutover
or source writes occurred. Detailed production evidence is retained privately
outside the repository. This does not cover other stores, real customer-link DB
records, SMB ACLs or company OneDrive.

Current source validation: 65 SmartDrive Node tests passed. Refreshed Maven gates
on 9 November ran 1472 SmartStock tests (158 optional skips) and 367 SmartStudio
tests (one optional skip), with no failures/errors. SmartStudio used the refreshed
local SmartStock dependency. A synthetic end-to-end Node CLI/Java renderer run verified the
prepared revision and an already-prepared second run. These checks did not
touch the live library or install/publish application artifacts.

Desktop-launcher regression checks on 9 November passed for stopped/running
guest service states, ordinary client opening, startup timeout and invalid URLs.
The launcher now lets the browser handle Cloudflare Access redirects instead of
rejecting an unauthenticated public-page probe. Service, browser and network
boundaries were mocked; installed shortcut behavior remains unverified. The
security script and whitespace check passed after this change. Browser inventory
was rechecked: local browser use remains disabled in Work settings, so visual
comparison with the approved mockup is still blocked.

The new-install service configuration now applies restart recovery to both the
account broker and web service. The 65-test Node suite and installer PowerShell
parse check passed afterward. Actual SCM recovery settings and service crashes
remain unverified. Update, repair and rollback now apply the same shared policy
before code activation; mocked SCM checks cover both service commands and either
command failing without continuing. Applied policy persists on later activation
failure, while services remain stopped for review.
The current session was rechecked as non-administrator and Hyper-V switch
inventory still returns an authorization error on POS-SERVER02.

| Requirement | Current evidence | Remaining work / acceptance |
| --- | --- | --- |
| Separate SmartDrive app with company branding | Branding/setup generator; installed rebrand planner and maintenance tool; ACL-preserving share recreation, protected journal, compatibility alias and workstation mapping/shortcut helper; Windows rename/hash/ACL/identity-stream fixture checks | Review rendered desktop/mobile screens; verify installed shortcut, native rebrand/share/service flow and workstation updates in the VM. Hostname/DNS changes are separate infrastructure operations. |
| Dedicated VM/network device | VM/storage scripts and inventory; Gen2 Secure Boot/local-key-protector/vTPM setup with readback verification; host-management external-switch preflight and failure fixtures | Create/install the licensed guest; verify actual TPM/Secure Boot and boot, assign hostname/IP and check discovery. Current session lacks Hyper-V permission; ISO path is missing. |
| 300 GB pilot and later 4 TB mirror | Storage readiness/planning/migration scripts; optional verified-copy expansion with shrink/space/health policy checks | D: rechecked with about 713 GB free. Create the VHDX, install the VM, and verify full migration/guest partition expansion on actual disks when available. No pilot VM has been deployed. |
| Persistent workstation drive login | Credential Manager/enrollment tools, durable enrollment/revocation, typed account broker; scoped SMB connection/activity observation with persistent last-observed timestamps and failure handling | Verify installed service/session observation, two-client enrollment, reboot reconnect and employee handoff. |
| Native apps and shared saves | SmartStudio file associations, native picker/paths, revision/save/rename tests | Install current artifacts and verify SmartStudio, Word and PowerPoint on two workstations, including disconnect recovery. |
| Explorer thumbnails and Preview pane | COM handler source/extraction checks; maintenance-window legacy preview preparation with verified originals, revision journal, identity/unknown-metadata/asset preservation tests | Verify actual-library rendering/fonts, installed registration, thumbnails, Preview pane and refresh after edits. Run preparation against the copied library before cutover. |
| Employee web access and store scope | Scoped listings/previews/session/gateway; reauthorized in-memory folder/file restoration after session expiry with fresh labels/manage flags and sign-out clearing; private-IP HTTPS origin and peer/certificate tests | Browser verification of login/restoration; configure real certificates, LocalService access, firewall and Tunnel/Access; verify connector trust and cellular store scope. |
| Manager permission assignable in SmartStudio | Permission migration, server-side gates, reused role editor entry point | Apply on the deployment database and verify assignment/revocation through installed clients. |
| Management dashboard | Storage/preview/enrollment/audit views; account-service probe; assigned-store backup summary and last verification; manager-only effective Settings with secrets excluded; scoped workstation activity | Browser QA and deployed dependency/service/backup/session health checks remain. |
| Scan ingestion and machine subfolders | Stable/closed-file intake, validation, Defender integration, durable publication/quarantine tests | Test actual service identity and Canon output; confirm C5040 SMB2/3 firmware or implement/test the documented intake gateway fallback. |
| Scanner management from dashboard | Add-copier form/API, durable enrollment, immediate runtime registration, processing pause/resume, credential enable/disable/rotation and scoped setup instructions; manager/CSRF/store gates and private audit; restart/failure and live-worker regression tests | Verify native creation, ACLs, credential/session behavior and actual Canon output in the guest. Interrupted partial setup is retained for administrator review. Processing pause alone does not disable a copier's SMB credential. |
| Shared library migration | Verified export/import, ACL publisher, identity streams and index tests; protected 201-design local production-copy rehearsal with hashes/access/previews/recovery checks | Rehearse every additional store's library; verify real sharing assignments, SMB ACL publication, customer links and recovery snapshot before cutover. |
| Existing customer submission links | Current-source bridge, legacy identity preservation, queue/record integration; isolated PostgreSQL regression tests | Verify copied real links/database and then deployed customer/employee workflows. |
| OneDrive selected-folder backup | Durable versions, resumable sessions, verification, retries, deletion retention and isolated-entry failure tests | Configure actual company destination/credentials; test outage, restart, quota and verified restore against that destination. |
| Disaster recovery | Cloud manifest discovery/rebuild, recovery-folder restore and identity preservation tests | Perform replacement-server recovery with actual configuration backups and company OneDrive. Confirm retention/quota policy with the owner. |
| Windows service packaging | Explicit payload copy/prerequisite checks; copied-layout startup test; staged update/repair and manifest-verified code rollback retaining current service/account authority; filesystem activation/rollback/corruption fixtures | Install and verify LocalService, broker, isolated preview account/firewall, actual service update/repair/rollback, recovery and reboot in the guest. |
| Validation | Node suite, full Maven suites, targeted native/Java checks, isolated submission database tests, security and whitespace checks have run successfully | Repeat appropriate gates after remaining changes; keep live/hardware checks separate. Optional tests in normal Maven runs are skipped unless their fixtures are configured. |

## Next source work

Review current screen structure against the approved red/gold reference and audit
remaining source gaps, then verify actual VM security/boot, HTTPS connector
integration, rebranding, legacy preview preparation
and the scanner lifecycle on the installed VM. Keep
production cutover disabled until real-library migration and installed-client
acceptance are complete.

## Deployment inputs still needed

The licensed Windows ISO path, an administrator-run Hyper-V setup, approved LAN
network/switch/IP information, real HTTPS origins and Tunnel configuration, actual
store IDs/library sources, the company OneDrive backup destination and server-only
credentials, and access to two Windows clients plus the copier for acceptance.
Secrets and production migration/recovery evidence must stay out of Git.
