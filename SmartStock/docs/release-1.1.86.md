# SmartStock 1.1.86 Windows quick update

Build 101086, prepared 26 October 2026 from the shared SmartStock working tree.
All SmartStock application sources, resources and database migrations were frozen
in D:/SmartStock-quick-update-1.1.86/source before packaging. SmartStudio is a
separate release and was not repackaged. No source changes were detected after
this freeze during validation.

Includes Orders layout and current-order queue improvements, visible work-type
add-ons, Work Queue workload columns, Sales Tasks, balance-sheet workspaces and
employee web tools, inventory/item web tools, shirt customization and previews,
customer phone ownership, overseas sorting, shared-template validation, and
printing/service fixes. Hosted Worker deployments are separate from this
Windows application quick update.

- ZIP: smartstock-windows-1.1.86.zip, 168,542,161 bytes.
- SHA-256: d6432bb0cf0f3517c3b9a1cc1372213ba6fa902952ea9dfe054a1c44ddc7902d.
- Model migration bridge SHA-256: ad77eca205d38621e5b84da61551db51308a237bec5b557b350ab9fb580d705e.
- Frozen Maven package: 1,362 tests, zero failures/errors, 146 skipped.
- Frozen web/gateway tests: 111 passed. Website tests: 69 passed; TypeScript passed.
- Repository security and whitespace checks passed.
- Model-free ZIP layout, version, runtime/licences, migration support and hosted
  model catalogue readiness passed.
- Exact ZIP update, rollback, reapply, persistent profile preservation, installed
  JAR identity, required resources, bundled runtime and native launcher passed
  in an isolated application/profile.
- The first acceptance harness held the previous app JAR open on its classpath;
  rerunning the updater from outside the installation passed. No application
  code was changed to resolve this harness-only Windows lock.

Evidence, logs, frozen-source hashes and validation.json are under
D:/SmartStock-quick-update-1.1.86/.

Quick-update feed only; no full installer or macOS release was built. No live
store installation, database/service upgrade or physical hardware acceptance
was performed. Verify signed-in workflows, phone access, database migrations,
printers, drawer and NFC separately. Update the store server first, then registers.

Published 26 October 2026. Full hosted object download matched exact size and
SHA-256 before update-feed metadata publication. Independent metadata read-back
confirmed version, build, platform, published status, object path, size and hash.
Object: windows/1.1.86/d6432bb0cf0f3517c3b9a1cc1372213ba6fa902952ea9dfe054a1c44ddc7902d/smartstock-windows-1.1.86.zip.
