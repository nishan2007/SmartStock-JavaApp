# SmartStock 1.1.107 Windows release

Prepared 5 November 2026, build 101107. Adds the display's manual Update Now control and running version label. Fixes the Windows updater's account identity translation failure by enumerating ACL entries as raw SIDs in both installation and maintenance scripts. Unknown principals with write access remain rejected.

## Exact artifacts

- Update ZIP: 168834262 bytes; SHA-256 bb07a9681a06402ede118278813d450daa3dce1be928c5996d26a3070becf24f.
- All-in-one installer: 581902616 bytes; SHA-256 26524d53ecb6a0fd7d68dbf2b4c0739b2820949817794d95fba72365a534f9b4.
- Model migration bridge: 3318 bytes; SHA-256 7d79ebd8d1f466bf1a2419786e61d859c87dedd5d6d78ad2e21dbea66d4aeb66.

## Validation

The full Maven package gate passed: 1460 tests, zero failures/errors, 157 skipped. Repository security and whitespace checks passed. Portal tests passed (19). PowerShell parser and synthetic unresolved SID read/write checks passed. The portal was deployed with its existing protected configuration as version 7935f7c5-42eb-40a2-ba1d-8200e01b44ea; the public Windows manifest endpoint returned the existing verified installer metadata afterward.

The exact model-free update ZIP was applied over an isolated 1.1.106 application. Installed JAR identity, native launcher refresh, runtime/profile preservation, model exclusion and native welcome-screen startup passed. The initial seven-second startup check timed out; the separate startup retry passed. The full installer was extracted and its exact application JAR, corrected maintenance/setup script hashes, model exclusion and isolated native startup verified.

No live application, scheduled task or store database was changed. Actual elevated installer/task repair, ACL enforcement under a standard user, automatic/manual service download and restart, hardware checks and macOS packaging remain separate acceptance steps. Extraction/startup does not prove an elevated installer transaction or task registration.

## Rollout

Displays affected by the identity translation error require the full installer run as administrator once to replace the protected maintenance script. The app-only ZIP cannot repair it. Update the store server before registers. Models remain separate and preserved. SmartStudio is not republished in this release.

Local packaging, acceptance and publication evidence is under SmartStock/target/release-windows-1.1.107. Protected configuration and downloaded verification evidence are not Git artifacts.

## Publication

The Windows app update was published after full hosted download, size and SHA-256 verification. Independent metadata read-back confirmed version 1.1.107, build 101107, published status, exact size/hash and the content-addressed updates/windows/1.1.107 object path. The initial Bash publisher verified its uploaded ZIP but stopped on a DNS error before creating metadata; the existing Node publisher completed publication with a process-local DNS fallback and normal HTTPS validation.

The full Windows installer selection was published after a successful complete hosted verification download. Earlier failed connection attempts left the previous selection unchanged. An independent public download returned HTTP 200 at 2026-11-05T15:39:06.306Z and matched 581902616 bytes and SHA-256 26524d53ecb6a0fd7d68dbf2b4c0739b2820949817794d95fba72365a534f9b4. The public /manifest/windows endpoint independently matched version 1.1.107, build 101107, size and hash. Public installer: https://downloads.deckers.gy/download/windows.
