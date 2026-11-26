# Store Transfer / Transfer Desk verification

Date: 30 October 2026

final result: passed

This report belongs to the Store Transfer redesign. The existing root
`SmartStock/design-qa.md` documents Sales Tasks and is preserved.

## Target and scope

- Source visual truth: `docs/store-transfer-design/reference.png`, the first
  displayed option selected by the user (1487 × 1058 pixels).
- Implementation: production Java/Swing `StoreTransferWorkspace`, hosted by
  `StoreTransfer`, using the authenticated LAN transfer APIs.
- Main comparison: same 1487 × 1058 content viewport, light theme, outgoing
  transfer #2048 selected in edit mode, three item lines, 18 units. No density
  normalization or browser/device frame is required.
- Fictional records and the bundled company logo are confined to the test
  fixture. Production uses the signed-in store/staff and configured branding.
- The selected reference's stock timing copy is corrected: source stock is
  deducted when sent, destination stock is added when received, and changes or
  cancellation finish after automatic destination acknowledgement.

## Comparison history

1. Initial render found P1 missing queue text and quantity controls in the
   component render, P1 missing table headers, P2 incorrect notes/summary widths,
   and a P2 grey main background. Fixed renderer layout, explicit table header
   hosting, proportional note/summary sizing, and the viewport surface color.
2. The recapture confirmed queue text, headers, quantity spinners and main
   surfaces. P2 typography/density drift was corrected by matching table and
   metadata sizes, footer action widths, queue row sizes and header height.
3. The next comparison found P2 clipped stock summary copy and inconsistent
   dark-theme renderer fields/borders. Replaced the summary label with wrapping
   text, added native field styling to rendered spinners, preserved empty scroll
   borders through theme updates, and strengthened semantic badge fills.
4. The final reference-size recapture and focused queue, editor, route/header,
   and footer comparisons confirm the fixes. A 1024-pixel stress check found P2
   overlapping footer actions and a cramped confirmation badge/lifecycle; the
   final compact capture wraps footer actions into two rows, wraps the badge,
   uses concise lifecycle labels and scrolls the item grid horizontally.
   No actionable P0/P1/P2 issues remain in the inspected states.

## Verification evidence

The isolated validation POM is generated under
`target/store-transfer-validation/pom.xml`. It preserves source, dependencies,
resources, compiler and test configuration while isolating compiled output and
reports. Test working directory remains `SmartStock`. Shared Maven output was
being rewritten by other jobs; those shared-output failures are not accepted as
verification evidence for this change.

Evidence is produced under `target/store-transfer-validation/evidence/`:
`workspace.png`, `comparison.png`, `incoming.png`, `awaiting.png`, `new.png`,
`compact-1280.png`, `compact-1024.png`, and `dark.png`.
Focused comparisons are `comparison-queue.png`, `comparison-editor.png`,
`comparison-header-route.png` and `comparison-footer.png`. Each places the
reference crop beside the implementation crop at the same scale.

`native-preview.png` captures the real, fictional-data Swing preview on Windows.
Its viewport is shorter than the reference, so it supplements the matched-size
comparison rather than replacing it. Computer Use verified New transfer,
focusing/typing in product search, the search-result popup, Down/Enter selection
and adding the correct product. Native preview error output was empty.

## Fidelity surfaces

- Fonts: Segoe UI, with matched title, queue, table, notes and footer hierarchy.
  Readable sizes and line wrapping were checked in full and focused comparisons.
- Spacing: 375-pixel queue at the reference size, right editor, route band,
  receiving notice, combined inventory editor, notes/summary, fixed action and
  status bars. Short windows scroll work content while preserving actions.
- Colors: shared Deckers orange, navy text, white surfaces, pale blue table/info
  bands, pale orange selection/summary, semantic pending/confirmation badges;
  existing-window dark-mode verification passes in the final capture, including
  rendered quantity fields, notes, semantic badges and fixed actions.
- Assets: actual bundled company logo in fixtures; configured company logo in
  production. Icons use the bundled licensed Font Awesome Free font. No screen
  content is a raster replacement for functional controls.
- Copy: stock timing and receiving/cancellation confirmation are truthful to the
  server transaction flow. No fabricated completion, approval, or connection
  status. Restricted sync status remains explicitly unavailable.

Remaining P3 native differences are font rasterization, native spinner/combo
arrows, modest border/radius differences and the closest licensed storefront/cube
icons. The screenshot's inaccurate stock-deduction copy is intentionally
corrected, and production identity/counts/connection state are actual data.

## Functional verification

- Native workspace/controller tests cover create, product identity, duplicate
  item merging, editable quantity commit, remove controls, note validation,
  unsaved-edit protection, filtered queue selection, contextual actions,
  permission denial, receiving confirmation from loaded lines, request-key
  reuse after failure, read-only confirmation state, busy/loading/error states,
  late-response suppression, disposal, and sending the original edit/receiving
  snapshot to the server.
- Disposable PostgreSQL verification covers edit deltas, product replacement,
  cancellation, duplicate/reordered envelopes, ownership/permission checks,
  legacy unsynchronized transfers and receiving races. The full baseline test
  exercises actual receipt allocation, receiving batches, destination stock
  addition, source stock deduction, repeat-receive rejection and rejection of
  stale reviewed quantities before receipt allocation or inventory mutation.
- Targeted native Swing/controller, transfer architecture, database, shared Sales
  Tasks and theme-contrast tests passed. Security and whitespace checks passed.
  Full isolated native Maven suite passed: 1,444 tests, zero failures, zero
  errors, 148 platform/opt-in skips across 465 suites. Source hashes and totals
  are recorded in `target/store-transfer-validation/evidence/source-provenance.json`.

## Deployment limits

No release is published and no installed application, live store database,
Windows service or physical hardware is changed. Two installed stores and hosted
synchronization still need deployment acceptance. Hardware is outside this
screen change.
