# SmartStock 1.1.85 Windows quick update

Build 101085, published 26 October 2026 from the frozen 1.1.84 release source
with only the shared badge-template validation fix and regression test added.
Unrelated working-tree changes are outside this release snapshot.

Fixes `Only reusable template fields may be shared` when SmartStudio saves
rotation, hidden layers or transparency. The server accepts and validates these
reusable settings while continuing to reject personal-photo/session fields.
Rotated layer dimensions follow the Studio client's existing validation.

- Update ZIP: `smartstock-windows-1.1.85.zip`, 168,493,900 bytes.
- SHA-256: `36dd451678e28df9f51f835d8cfca0ce5feea381e731fd63f2fce3cb84f90ea3`.
- Model migration bridge SHA-256: `502919dc545c0e77d4a50a0a948bd73ee872d0e57b838200a6bf4dcf95906c12`.
- Maven: 1,347 tests, zero failures/errors, 143 skipped.
- Model-free archive verification, repository security check and scoped
  whitespace checks passed. Hosted model-catalogue readiness passed.
- Isolated 1.1.84 update, rollback, reapply, persistent-profile preservation,
  exact installed JAR identity, bundled Java and native launcher checks passed.
- Exact Studio 0.1.81 serializer to exact server 1.1.85 save/read passed;
  nondefault layer settings survived and personal photos were rejected.

Evidence and final publication logs/metadata are under
`D:/SmartStock-template-save-1.1.85/`.
The full hosted download matched size and SHA-256 before metadata publication;
independent read-back confirmed version, build, platform, object path, size,
SHA-256 and published status.
Apply the server update before SmartStudio 0.1.81, then registers.
Quick-update feed only; no full installer or macOS release was prepared.
No live store installation, database/service acceptance or physical hardware
acceptance was performed. Live paired shared-save remains a rollout check.
