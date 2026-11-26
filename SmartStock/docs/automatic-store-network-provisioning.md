# Automatic store network provisioning

The central Cloudflare broker is deployed. The application-side integration is staged
source and has not been published or installed as an application update. Skeldon's
separately installed renewal remains active. A live Skeldon pilot verified signed
registry authorization and issued a trusted certificate through the broker, without
changing the running browser identity or website settings. Rosehall has not been piloted.

## Assigned store addresses

For the database location ID `N`, the broker assigns:

| Purpose | Hostname | Destination |
| --- | --- | --- |
| Store Wi-Fi / LAN | `store-N-lan.deckers.gy` | Reserved private IPv4, browser HTTPS port 8444 |
| Staff website / Deckers home-screen app | `store-N-web.deckers.gy` | Outbound tunnel, loopback employee listener on 8450, `/employees` only |
| Administrator maintenance | `store-N-admin.deckers.gy` | Outbound tunnel, loopback SSH on 22, Cloudflare Access and existing SSH authorization |

These are distinct security boundaries. Neither the HTTPS register API on 8443 nor the
LAN browser listener, database, printers, cash drawers or network probe is tunneled.
Cloudflare holds the website edge certificate. The store generates and keeps its own
LAN certificate private key. The tunnel validates its HTTPS upstream using the public
register certificate and a certificate-matching server name; TLS verification stays on.

The broker uses the existing cloud **coordination registry**, not the live POS database.
Each request is signed with the existing register TLS identity. The broker checks its
fingerprint, store assignment, PRIMARY role and generation against the registry on every
operation. Requests expire after two minutes and cannot be replayed. The store cannot
choose another store's hostname or alter unrelated DNS. A replacement server fences the
previous connector before moving its DNS records; old live SSH sessions still require
explicit termination during handover. Registry unavailability denies new cloud changes
and does not stop local store operations.

## Central deployment requirements

Deploy `cloudflare/smartstock-network-provisioner` as a separate production Worker with
its `StoreNetwork` Durable Object binding. Configure these values through deployment
settings or the secret manager, never committed files:

- `CF_ACCOUNT_ID`, `CF_ZONE_ID`: the approved Deckers account and zone.
- `CF_API_TOKEN`: central token restricted to that zone's DNS editing and the account's
  Tunnel and Access application/policy operations. It is never sent to store servers.
- `SUPABASE_URL`, `SUPABASE_SERVER_KEY`: production coordination project and server-only
  RPC credential. This credential is not an employee token and never goes to registers.
- `ADMIN_ACCESS_POLICY_ID`: a pre-approved reusable **Allow** policy with explicit users
  or groups and a required MFA authentication method. No Everyone or Bypass policy.
- `ADMIN_ACCESS_TEAM`: the Cloudflare Access team name.
- `ADMIN_SSH_PUBLIC_KEYS_JSON`: approved maintenance **public** keys, without comments or
  authorized-keys options. Private SSH keys remain only on authorized operator computers.

The production configuration enables LAN and remote provisioning following operator
approval of central credentials and an owner-only MFA policy. Older installed server
code can complete the LAN pilot but cannot enable remote routes: the broker requires
the updated client's managed-routing capability. No production store tunnel has been
created by the broker yet. New deployments must review CA terms, token permissions and
the actual Access policy before enabling either flag.
No broad zone credential needs to be installed on Rosehall or future store servers.

## Windows server onboarding and updates

The packaged background server starts the provisioning watcher in production only.
Registers, Development/Test instances and inactive server roles cannot start it.
Registration with the existing Server Setup Wizard and a verified PRIMARY assignment
are prerequisites. The watcher runs outside the Swing event thread and retries every
15 minutes. A cloud provisioning failure does not block LAN startup or local sales.

If the server has exactly one usable physical private IPv4 address, setup can select it.
When there is more than one, choose **Store network address** in the final Server Setup
step. Advanced repair can use the machine-local file
`~/.smartstock/network-provisioning/network.properties` with `lan.address=<chosen-IP>`.
Setup requires that address to be statically configured. Confirm a matching router
reservation separately; the app does not silently reconfigure an unknown router.

The watcher uses packaged PowerShell resources, a pinned Posh-ACME release from
PowerShell Gallery, and a custom DNS-01 plugin. The plugin signs narrowly scoped broker
requests using the installed Java runtime. It never receives the central Cloudflare
token. Certificate data and DPAPI passwords stay outside application/rollback folders.
Successful changes reload only the mobile browser listeners, preserving the register
certificate and LAN API. A known standalone legacy renewal task is disabled only after
normal HTTPS verifies the new running browser identity, preventing duplicate renewers
from repeatedly restarting the service. Existing employee origins are preserved during
migration; managed origins are accepted only for this server's assigned store. New
employee listeners follow their managed origin automatically;
the existing administrator's employee-web enable/disable setting is preserved.

The connector token is scoped to this server's tunnel. `cloudflared --token-file` needs
an owner-only plaintext token file; its parent directory allows only the server user
and SYSTEM. Tokens never appear in process arguments, endpoint summaries or repository
files. The managed connector task runs under the same logged-in Windows user as the
existing background server. Server shutdown and inactive roles stop the managed connector.

For a new local Windows server account, elevated production setup installs Windows
OpenSSH Server, binds it only to loopback, disables password authentication and limits
login to the server account. It does not overwrite an existing SSH configuration.
The watcher installs only centrally approved public keys into its marked managed block,
preserving unrelated keys. Existing SSH installations need their authorization checked
separately. Domain-account SSH setup is not automated.

Public SSH host keys are published only after a request signed by the registered server;
the broker checks current PRIMARY ownership again on each host-key lookup. The
`tools/connect-store-server.ps1` helper retrieves that verified binding and keeps strict
SSH host-key checking on while connecting through Cloudflare Access. Private host keys
never leave the server. Remote work still requires an authorized session, Access/MFA
login and the operator's approved SSH private key; this does not create an unattended
assistant account. Access can be revoked centrally. Mac server onboarding is not
implemented by this Windows provisioning watcher.

New pairing QR links use the current store's managed website address. Existing shared
`deckersapps.deckers.gy` installations continue using their existing gateway; changing
that single-store gateway is a separate migration. Moving an existing phone to another
origin may require normal administrator re-pairing. Do not silently copy browser sessions
or select a store from an unsigned query parameter.

## Rollout and verification

Validation completed: the Worker security suite, Maven suite and final provisioning/
installer tests passed; repository security and whitespace checks passed. The Windows
packager produced a model-free update ZIP and all-in-one installer with verified sizes
and SHA-256 sidecars. These are validation artifacts using the existing shared version,
not a newly published release. An isolated per-user install with a separate Development/
Test profile launched and opened the packaged server wizard. The test installation was
removed afterward and original shortcuts were verified/restored. This smoke test did
not run production database setup, change live store services, or exercise elevated
OpenSSH installation. Rosehall, iPhone, external Access/MFA/SSH, handover, and hardware
checks below remain outstanding.

Skeldon's already verified static address is saved as its future managed network
selection. Rosehall's address must be detected or selected on that server; do not copy
Skeldon's address to Rosehall.

1. Run Worker security tests, Maven tests and the repository security gate. Build the
   platform package using the release guide; this source change does not authorize release
   publication or replacing live stores' installed applications.
2. Configure the broker's secrets and Access policy, deploy to a pilot environment, and
   verify the real registry RPC and Cloudflare APIs with a registered pilot PRIMARY.
3. Test unsigned, expired, replayed, wrong-store, standby and replaced-server requests.
   Confirm unrelated DNS conflicts stop setup instead of overwriting records.
4. Verify normal DNS and trusted HTTPS from a second device and an iPhone home-screen
   app, including an authenticated local-only screen and an off-LAN denial.
5. Verify the actual connector task, approved-user Access/MFA login, unapproved-user
   denial, SSH authorization and pinned host key from outside the store network.
6. Exercise real renewal, restart, store handover and rollback. Check logon-start tasks
   after a Windows restart. Inspect `network-provisioning/last-run.log`, `status.txt` and
   the non-secret `endpoints.json` for readiness; configuration alone is not live proof.

Do not delete identity keystores or models for rollback. Disable central provisioning,
stop the managed connector task, and restore the saved browser/employee settings only
after checking which server is still the approved PRIMARY. Keep a working LAN identity
and a renewal path throughout the rollback.
