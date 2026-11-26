# Roles and permissions — option 1

final result: passed

The selected target is `selected-reference.png` (the first displayed generated mockup). Implementation is the native Swing `Roles_Permission` screen, not a web prototype.

## Evidence

- `comparison.png`: source on the left, final implementation on the right, both 1488 × 1058 pixels at 1:1 scale. No stretching or OS title bars.
- `permission-detail-comparison.png`: corresponding permission rows at 1:1 scale, source left and implementation right.
- `implemented.png`: final light-mode workspace, Sales Associate / Custom Orders / two pending changes.
- `implemented-dark.png`: corresponding dark-mode capture.
- `implemented-small.png`: 1024 × 720 companion-window capture. Lists scroll vertically; permission explanations wrap.

All implementation captures use synthetic data and the in-memory gateway in `test/ui/screens/RolesPermissionsPreview.java`. They contain no real store policies or credentials. The source and final implementation were opened together in the full comparison and the focused comparison before this result was assigned.

## Iterations and disposition

1. Initial native render: blocked. Default Windows tabs/buttons were undersized, fields had no visible search prompts, and the right pane underused the reference's typography and hierarchy.
2. Corrected the role sidebar width, heading and permission type scale, shared Deckers accent buttons, selected-role band, category navigation, visible descriptions, and persistent footer. Added the existing licensed Font Awesome search asset.
3. Intermediate comparison: blocked. Six reference permission rows did not all fit; the narrow category column could scroll horizontally; dark menu colors were unreadable. Reduced row padding, made viewport content track its width, wrapped explanations, and styled the native menu surface explicitly.
4. Final comparison: passed. The reference composition, selected state, text hierarchy, orange active accents, purple selection bands, white/light slate surfaces, sensitive labels, searches, desktop/mobile tabs, and footer are reproduced with functional native components. All six reference permission rows are visible at the reference viewport.

## Required fidelity surfaces

- Fonts and typography: existing SmartStock SansSerif family, 40px page heading, 34px role heading, 21px permission titles, 18px descriptions, and 20px principal actions. Native font rasterization differs from the generated reference.
- Spacing and layout: role sidebar, role header, device tabs, search row, category rail, permission detail list, and fixed action footer match the selected direction. Independent vertical scrolling keeps the footer available in smaller windows.
- Colors and tokens: `DeckersPalette` and `DeckersSwing` provide surfaces, text, borders, orange/purple accents, and light/dark behavior. No new company-wide theme setting was invented.
- Image quality: standard native checkbox artwork is reused at a larger hit size; search icons use the repository's existing licensed font. No screenshot is used as application UI.
- Copy and content: matching synthetic reference content was used for comparison. Production labels, groups, descriptions, role names, and grants come from the authenticated LAN API. The generated D mark is not hardcoded; production uses configured company name/logo through the existing company-customization gateway.

## Functional checks

Seven native UI tests passed with `java.awt.headless=false`:

- Permission filtering and device tabs retain drafts.
- Existing grants absent from the visible list are retained.
- Discard restores both scopes and the description.
- Save sends a single selected role's full policy and updates its description baseline.
- Search finds matching permissions in other categories.
- Late responses cannot overwrite a more recent role selection.
- Company-defined mixed-case names remain intact when displayed; older servers keep permission editing but cannot offer unsupported role metadata/copy operations.

Two server tests passed: all role operations reject unauthorized actors before writing, and oversized descriptions fail before policy replacement.

Native computer-use verification also confirmed review → save → no pending changes, cancellation of a role switch preserving the draft, discard restoring saved access, and role duplication copying both the description and selected permissions in the isolated gateway. The restyled review dialog was exercised after its final change.

Validation commands executed:

```powershell
mvn -q -f SmartStock/pom.xml test
mvn -q -f SmartStock/pom.xml '-Dtest=RolesPermissionsTest,LanRoleAdminServiceTest' '-Djava.awt.headless=false' test
& 'C:/Program Files/Git/bin/bash.exe' SmartStock/tools/security-check.sh
git diff --check
```

The full suite passed before the final visual refinements; the final implementation was then compiled and the native UI/server tests and security/diff checks passed again.

## Persistence and compatibility

Role descriptions use the existing `roles.description` column. The server saves descriptions and both grant sets in the existing authenticated/idempotent transaction. Duplication copies the saved description and desktop/mobile grants inside the role-creation transaction. Existing role policy synchronization already includes descriptions and both permission scopes; no schema migration or new index is needed. Register-side code receives no server credentials.

An older server omitting the description field leaves ordinary permission editing enabled but disables the new metadata and copy controls with an update explanation. Custom role creation remains available.

## Remaining P3 polish and verification limits

Native control bevels, border corners, and font rasterization differ slightly from the generated image. The nonessential category summary can truncate at the smallest companion viewport; actionable permission descriptions wrap and remain available through scrolling. These do not block the selected workflow.

This is source implementation and isolated verification. No release was published, service replaced, application installed, or live store permission changed. Real PostgreSQL persistence/rollback, paired LAN service behavior, configured company-logo loading, and the installed Windows application still need deployment-environment verification. Printers, cash drawers, and NFC readers were not exercised and are unaffected by this change.

The pre-existing `SmartStock/design-qa.md` and unrelated working-tree changes were preserved; this screen's QA record is scoped to this directory.
