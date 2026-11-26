# SmartStock 1.1.90 Windows update

Published October 26, 2026. Build **101090**.

This update adds **Web App** and **Pair Balance Sheet Device** controls to the
desktop Balance Sheet. Pairing QR codes open the dedicated
`https://balancesheet.deckers.gy` workspace. Sign-in requires Balance Sheet access,
without granting or requiring Employee Web App access. The browser app retains
its dedicated install icon and supports redeeming a fresh activation link at
the new hostname. Approval cookies remain scoped to each hostname.

## Verified artifact

- ZIP: `smartstock-windows-1.1.90.zip`
- Size: **168,564,112 bytes**
- SHA-256: `f32a9565c7d481031eabc379603bd70107937ab6af1664abea71c01c23c57cc8`
- R2 object: `windows/1.1.90/f32a9565c7d481031eabc379603bd70107937ab6af1664abea71c01c23c57cc8/smartstock-windows-1.1.90.zip`
- Model migration bridge SHA-256: `210d69645902ff14c1d6c9cbea7e687a442eb5f73d39376a07590114082af37c`

## Validation

- Built from 2,462 frozen source files in
  `D:/SmartStock-release-1.1.90-balancedomain/source`. Their hashes were rechecked
  against the captured manifest after packaging.
- Maven: **1,382 tests**, zero failures/errors, **146 skipped**.
- Web/gateway: **143 passed**. Balance Sheet browser acceptance passed, including
  captured input, retries, offline recovery, snapshots, comparison, phone layout,
  activation-link redemption and rejection of foreign activation URLs.
- Security and `git diff --check` passed.
- Model-free archive layout/version/runtime/licence checks passed. The existing
  hosted independent model catalogue was verified; no model weights were uploaded.
- The exact ZIP passed isolated 1.1.89-to-1.1.90 update, rollback, reapply,
  persistent-profile preservation, installed JAR/resource identity, bundled Java
  runtime and native launcher acceptance. Pairing controls and the dedicated
  public URL were checked in the installed release.
- The full hosted ZIP was downloaded and its size and SHA-256 verified before
  publication. Independent release metadata read-back confirmed version, build,
  Windows platform, published status, object path, size and SHA-256.
- The deployed Balance Sheet website, all eight asset hashes, store health,
  activation requirement and gateway route isolation were reverified.

Logs and acceptance results are under
`D:/SmartStock-release-1.1.90-balancedomain/`.

This is an **in-app Windows update**. No first-install selection, macOS package,
live installation, database or service restart was performed. Update the store
server first, then registers. Live service/database upgrade, signed-in device
workflows, real printer output, cash drawers and NFC remain rollout checks.
