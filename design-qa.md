# Deckers lavender home-screen QA

final result: passed

## Visual target and implementation

Source: C:/Users/decke/.codex/generated_images/01a1f662-f657-7563-afd4-43e3198e13a0/exec-01328281-fba8-4f42-91ea-b3e3baa16e82.png (852 × 1846 pixels).

Rendered implementation: output/deckers-home-lavender/home-390.png (390 × 844 pixels, viewport 390 × 844 CSS px, DPR 1). State: signed-in fictional employee, all 14 allowed apps, Home active, proven synthetic LAN connection. Production HTML/CSS/JS runs with a disposable synthetic API; no live account, database or store service is used.

Equal-size visual comparison: output/deckers-home-lavender/comparison-final.png. The source was normalized to 390 × 844 and placed beside the actual browser capture. The full-view image was opened and inspected. All labels, icons and navigation remain readable at this scale, so separate focus crops were not necessary.

## Iterations

1. Initial raster assets were being produced; layout was measured without treating missing assets as a passing screen. Once all 14 assets arrived, the browser caught final-row labels extending beneath fixed navigation (P1).
2. Normalized assets to 192 × 192, removed only outer transparent margins, and adjusted icon size, row gaps, first-row height, header/logo size and search spacing. All labels now fit above the navigation at 390 × 844. See comparison-1.png and comparison-2.png for intermediate captures.
3. Consolidated the theme CSS, repeated browser acceptance, and opened comparison-final.png. No actionable P0/P1/P2 visual issues remain. Bottom navigation also reserves the iPhone safe-area inset.

## Fidelity surfaces

- Typography: retained the shared system sans-serif family; matched 27px workspace title, 15px connection status, 16px search and compact 14px bold tile labels. The generated reference has slight raster antialiasing differences (P3).
- Spacing/layout: same three-column/five-row order, rounded search field, 75 × 72px icon slots, header and bottom navigation. At 390 × 844 the last two-line label ends at y=757.4, above navigation at y=773. All 14 tiles remain visible. Small row/alignment differences of roughly 1–6px are P3.
- Colors: purple header, rich purple text/symbols, pale lavender tiles, orange accents and warm-white canvas. The original multicolor logo remains unchanged. No charcoal tile background remains.
- Imagery: all 14 supplied-design-inspired icons are independent generated raster assets. No CSS drawings, emoji or handmade SVG substitutes. Assets share lavender/purple/orange art direction. Pictogram details and softly shaded tile backgrounds differ slightly from the generated source (P3); all screen semantics match. The preview portrait is fictional; production continues to show the signed-in employee's managed photo with initials fallback.
- Copy/content: all 14 existing app names and ordering match. Server-provided visibility and local-only flags control which tiles/badges appear. Connection text is shown as connected only after the existing authenticated LAN proof succeeds.

## Functional verification

User approved local Playwright for browser verification. Acceptance script: tmp/home-lavender/acceptance.cjs; results: output/deckers-home-lavender/browser-acceptance.json.

Verified all 14 images load; six dynamic local-network badges; search and no-results states; narrower server permission response removes unavailable tiles; Home/Account navigation; off-network Receiving shows the existing connection-required dialog; and responsive 320, 390, 430 and 768px widths without horizontal overflow. No browser page errors. Captures include home-320.png, home-430.png, home-768.png, account-390.png and network-required.png.

All 43 selected JavaScript tests passed. Targeted Maven DeckersWorkspaceTest and EmployeeWebSecurityTest: 12 tests, zero failures/errors/skips. Resource checks validate every allowlisted icon decodes as a 192 × 192 PNG. Repository security and whitespace checks passed.

## Scope and remaining acceptance

This change updates real workspace resources and server asset mapping. Employee/device permissions, single login, session policy, server-side authorization and LAN action checks remain in place. Real iPhone installed-app and live server acceptance are separate. No installation, restart, package publication or live data mutation was performed for this visual change.

## Checklist

- [x] All 14 assets integrated and optimized (about 620 KB combined).
- [x] Source/rendered comparison completed; P1/P2 issues corrected.
- [x] Responsive navigation, filtering and permission/network states verified.
- [x] Regression, asset, security and whitespace checks passed.

## Payroll and shared headers — 2026-11-21

final result: passed

The selected Payroll layout and shared app masthead were compared against the
updated Inline Payment mockup. See SmartStock/docs/payroll-design-qa.md for the
comparison, P2 fixes and verification scope. Evidence is under
SmartStock/target/payroll-design/, including comparison.png, responsive captures,
and header-results.json for all 15 mounted screens. Verification used the
previously approved local Playwright fallback because local browser access was
unavailable. Fixtures use sample data; deployment and real-iPhone acceptance are
outstanding.
