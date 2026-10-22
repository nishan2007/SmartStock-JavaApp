# SmartStock 1.1.49 (build 101049)

Windows update ZIP only for Device Management computer-name reporting. Enrollment
and authenticated metadata updates now replace a name that followed the prior
hostname or local username, while retaining distinct administrator friendly names.
Copied server identities still require separate device enrollment; this release
does not reset device identity, credentials, approvals or drawer assignments.

The exact published 1.1.48 archive was verified against its recorded SHA-256 before
comparison. All dependency entries are byte-identical. Inside the application JAR,
only LanApiServer and its nested classes, the manifest, and Maven version metadata
differ. No application resources or other classes changed. Comparison evidence is
saved with the release artifacts outside Git.

The standard Windows packager ran 1,111 Maven tests with zero failures/errors and
79 skips. Repository security and whitespace checks passed. Ten PostgreSQL naming
cases passed in a disposable cluster, covering renamed, custom, username-derived,
blank and null names across both naming SQL expressions. Model-free archive layout
and hosted model catalogue readiness passed. The exact update was extracted into
an isolated native-launcher layout, and opened the welcome window using a fresh
user profile. This is launch acceptance, not a production installation or a live
server/database upgrade.

Artifact: D:/SmartStockReleases/release-windows-1.1.49/smartstock-windows-1.1.49.zip
Bytes: 167002370
SHA-256: 588dc0b4cd2d3b78c2c3f52043eb062e8b6e22bf19da77056279056a8b53de39

No installer or model was built or selected for publication. macOS packaging,
live Windows installation/service upgrade, printers, drawers and NFC remain
separate checks. Update the server before registers; publication does not install
or restart running applications.

Published on 18 October 2026 after the protected publisher downloaded the entire
hosted artifact and matched its exact size and SHA-256. A separate metadata GET
confirmed version 1.1.49, build 101049, 167002370 bytes, the recorded SHA-256,
immutable object path, and published state. The public Windows installer selection
was checked before and after publication and remains 1.1.48. No live application
was updated or restarted by this publication.
