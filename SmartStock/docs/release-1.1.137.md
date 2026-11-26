# Mobile Management policies and device details — 1.1.137

Windows update-only release built from published 1.1.136 plus the completed
manager-controlled login policy, browser device reports and guarded migration
corrections. Unrelated source changes were preserved. No full installer or model
publication is included.

- Mobile Management owns **Keep employees signed in on this device**, off by
  default. Employee sign-in has no checkbox or accepted persistence preference.
  Policy changes end only that device's workspace sessions. Other devices and
  app/employee grants remain unchanged. Older clients preserve an omitted policy.
- **Device details** displays browser/OS, model when supplied, screen information,
  app mode and report time. Missing model data is labeled rather than guessed.
  Reports are diagnostic, bounded and rate-limited; server-derived paired-device
  identity, active employee session and CSRF are required. Reporting cannot change
  approval, permissions or policy.
- Existing-store upgrade hooks now apply the remembered-session column and new
  policy/report columns before schema validation. Migrations guard against
  unrelated catalogue drift and update schema fingerprints atomically.

The earlier live missing-column repair remains a separate maintenance action;
this task installs nothing and changes no live database or device setting.

## Verification

The frozen package passed 44 focused Java tests with zero failures/errors/skips,
including real disposable PostgreSQL migration, session, device-isolation and
administrator-boundary tests, Auth protocol tests and displayed Swing layout
checks. All 46 selected web tests passed. Security and whitespace checks passed.
The full unrelated Java suite was not rerun for this quick update.

Exact final JAR/ZIP content checks covered the server/client/schema/UI classes,
new migration SQL, collector script, manifest, home resources and all 14 icons.
Model-free ZIP and hosted model catalogue verification passed.

- ZIP bytes: 170375885
- ZIP SHA-256: e7730ef4acc1fea9067275d5f88e745ea0acbd64411c0d744d2f6f55221d2fff
- JAR SHA-256: 00f1e7d871ec535cbadd7a5c6eada7b7dbaa3c72047b90cda056e3b82134eb8a

Evidence is retained outside Git at `D:/SmartStock-release-builds/1.1.137`.
Install the store server first, then the workstation used for Mobile Management.
Real iPhone/Android installed-app, live database/service, physical hardware and
macOS acceptance remain separate checks. No application installation or service
restart was performed.

## Publication

Windows 1.1.137/build 101137 was uploaded, downloaded in full and verified by
size/SHA-256 before release metadata publication. Metadata was independently
checked against the exact version, build, object path and bytes.

Five legacy staff gateways were deployed with existing variables retained and
prior deployment records saved. Their new collector asset route reaches the
store backend; the current older backend returns METHOD_NOT_ALLOWED until the
server update is installed, as does the unchanged canonical Deckers gateway.
No claim is made that device reports are already being collected in production.
An initial gateway deployment attempt stopped before upload because prepared
static-asset directories were absent; existing preparation tools generated them,
and all five subsequent deployments and route checks completed successfully.
