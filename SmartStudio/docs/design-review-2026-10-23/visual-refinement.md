# Task Ribbon visual refinement — 23 October 2026

Published in verified Windows quick update 0.1.72. Published 0.1.71 remains immutable and verified.

The original Task Ribbon and selected Home/Insert images are the visual reference. Later user decisions take precedence: retain specialised tabs, put actual Layout/Print/Text/Picture fields in the ribbon, align Save/Print/Export with tabs, and remove the repeated document/workspace heading. The illustrated business-card artwork is synthetic reference content, not a replacement for user templates.

## Comparison and corrections

| Surface | Difference found | Correction |
| --- | --- | --- |
| Shared shell | Flat utility Workspaces button; inconsistent header and page backgrounds | Purple gradient Workspaces, plum navigation, warm background and near-white ribbon surface |
| Ribbon | Square edges, bottom captions, small solid tiles and duplicated button fills | Rounded outer clip, top-aligned group captions, wider rounded tiles, outline text/image/template icons, single background, purple active state and visible keyboard focus |
| Text | Unlabelled tightly packed controls and rectangular native fields | Separate Font/Style/Size/Colour labels; rounded font/style dropdowns and spinner; selection models/listeners retained |
| Layout and Print | Square legacy fields, inconsistent secondary buttons | Shared rounded field/button treatment with labels and live values; no scrolling routine control strip |
| Canvas | Heavy dark document border | Thin neutral paper border with subtle shadow; artwork geometry and export dimensions unchanged |
| Layers | Native table borders and heavy icons | Rounded movable card, soft lavender selection, outline icons, row dividers, scrollable list, fixed footer; visibility/order/delete behavior retained |
| Passport and PDF | Duplicate output toolbar row | Output actions now share the tab row, using the same Save/Print styling |
| Compact layout | Extra insertion row while inline settings were already visible; repeated footer rows | Quick actions only accompany collapsed ribbons; template size/status share one footer row |
| Dialogs and chooser | Mixed legacy rectangular control styling | Shared Studio-scoped rounded controls, consistent fonts and colours; existing actions and permissions retained |

## Review steps

1. Shared card editor: Home/Insert reviewed against the selected proposal. Latest user layout decisions retained. Existing commands remain available; wider Arrange groups compact when space is insufficient.
2. Layout/Print Setup: native component tests check all control bounds for 1366 and 1920 screens at 100%, 125% and 150% logical budgets, and verify live unit/print model changes.
3. Text/Picture: existing tests exercise selected-object changes, typed sizes, family/style choices, undo and saving after UI delegate changes.
4. Layers: synthetic reference render and long-list render reviewed; interaction tests cover nonmodal opening, selecting, hide/show, ordering, deletion and template persistence.
5. Specialist panels: Passport Pictures, PDF Editor, Image Studio and workspace chooser rendered with the same shared styling. Business Cards, Labels, Banners and Bill Books share the publication shell; Badge Cards uses the shared shell too.

These are native Swing component renders in an isolated development profile, not a claim of an authenticated installed-app UX audit. Native computer capture is unavailable in this session. The comparison page includes the original proposal, released component render and revised renders. Screenshots alone do not establish accessibility compliance; physical DPI, keyboard/screen-reader acceptance, live server operations and physical printing remain separate checks. No database, backend or public API change is part of this refinement.

Validation completed: the full Studio suite ran 277 tests with no failures/errors and one skipped test; focused ribbon/field/dialog/Layers checks after final visual tweaks passed. Repository security and whitespace checks passed. Updated component renders are included in `visual-refinement.html`.

The approved login and exact Creative Brush v4 identity were subsequently implemented in working revision 0.1.72. See `visual-refinement.html#login` and `../login-and-creative-brush.md`; company-logo branding is distinct from application identity. Full build validation increased to 281 tests, no failures/errors, one skipped. These changes were published in verified Windows quick update 0.1.72.
