# Payroll and shared masthead QA — 2026-11-21

final result: passed

## Visual target and evidence

Selected target: the updated Inline Payment mockup, with Payroll in the plum
masthead and Store above Pay period:
`C:/Users/decke/.codex/generated_images/01a1f662-f657-7563-afd4-43e3198e13a0/exec-fcb48aed-ed7a-4ad2-b931-e6f50f51156f.png`.

Evidence is in `SmartStock/target/payroll-design/`: `comparison.png`,
`payroll-final-390.png`, `payroll-final-full.png`, responsive captures at 320,
360, 390 and 768 pixels, and all 15 `header-*.png` captures with
`header-results.json`.

The comparison crops the app-owned portion out of the source presentation board,
scales it proportionally to 390 pixels wide (approximately 814 pixels high), and
places it beside the 390 × 844 implementation viewport. The implementation shows
the same All stores, Nov 16–22 period, employee amounts, unpaid/part-paid/paid
states, and pending G$20,000 payment as the target. Fixture portraits use the
supported initials fallback; production uses the employee photos supplied by the
server. Native date controls use the browser's locale format.

## Iterations

The first comparison found P2 vertical density differences, missing child icon
font glyphs, and inactive Home navigation while viewing a mounted screen.

- Compact selectors, employee rows and the payment footer now keep all three
  sample employees and totals in the phone viewport. Payment method expands from
  the footer; its summary has a minimum 24-pixel target.
- Calendar, store, disclosure and back icons now use the existing globally loaded
  Deckers icon font, avoiding unsupported shadow-root font registration.
- Home remains the active navigation destination while inside an app screen.
- Payroll, Home, Account and the other app names occupy the shared masthead.
  Redundant child branding is hidden without removing workflow controls.

The revised captures were compared with the source again. No outstanding P0,
P1 or P2 visual findings remain. P3 differences are native date formatting,
real-data photo/initial fallback, and the compact accounting-method control added
to support existing cash/bank recording.

## Interaction checks

The isolated Playwright fixture verifies exact formatted amount entry, a partial
payment followed by full settlement, store filtering, pay-period switching, All
stores outside the LAN and current-store selection after a successful LAN probe.
All 15 app headers render with no JavaScript page errors. Responsive checks show
no horizontal overflow at the four captured widths. Screens remain scrollable
with the existing safe-area-aware bottom navigation.

The fixture and screenshots contain sample employees and payments. Live iPhone,
certificate trust, installed-server and real payroll acceptance remain deployment
checks. The in-app browser was unavailable; verification used the user's approved
local Playwright fallback.
