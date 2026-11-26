# Deckers workspace visual verification

final result: passed

The existing repository and SmartStock project `design-qa.md` files were preserved.
This report covers the Deckers workspace change only.

## Reference and evidence

Reference: the selected charcoal mockup with Add Item and Receiving Inventory in
the main grid and the network-required bottom sheet. The original generated image
is 978 × 1610. It is preserved in `output/deckers-workspace/selected-reference.png`.

Rendered evidence uses the production shared-mobile HTML, CSS, JavaScript, font
and icons through the loopback-only fixture described in `deckers-workspace.md`.
It does not represent a deployed store. Screenshots are saved at the repository
root in `output/deckers-workspace/`:

- `home-390.jpg`: 390 × 844 home screen.
- `network-390.jpg`: same viewport with the store-network sheet open.
- `login-390.jpg`: matching employee sign-in.
- `receiving-login-390.jpg`: existing Receiving form with shared login styling.
- `home-320.jpg` and `home-768.jpg`: small-phone and tablet checks.
- `comparison.jpg`: reference and rendered network state together, with source
  aspect ratio preserved.

The source image has a shorter aspect ratio than the requested iPhone viewport.
The comparison keeps the reference at its natural ratio and uses accessible
field/button sizes in the implementation; it does not claim pixel identity.
The complete authorized catalogue contains two more existing tools than the
illustrative reference. These flow naturally in the same grid.

## Iterations and result

The initial phone rendering spaced rows too far apart, placing the store-only
tiles unnecessarily low on the page. Mobile header padding, icon dimensions,
row spacing and label spacing were tightened. The later capture and comparison
show the two tiles integrated alongside the other apps, with no separate store
tools panel. Charcoal header, original Deckers logo, warm-white surface,
three-column phone grid, orange network badges and Home/Account navigation match
the selected direction. Tablet layout uses four columns.

The native modal uses the same title, explanation and Got it action as the
reference. It makes background navigation inert while open. The modal is shown
only after the selected LAN app fails its direct store check.

Browser interactions verified: search narrows the actual app list; restricted
and empty states display correctly; ordinary app navigation occurs; both Add
Item and Receiving Inventory show the network sheet when the store cannot be
reached; Got it dismisses it; account navigation, logout, sign-in and authenticator
verification complete their expected fixture flows. Logout removes private app
and employee data. The unapproved-device state offers the single approval
workflow and existing iPhone-pairing recovery.

At 320 and 768 widths, document scroll width equals client width. All visible
source images load. Input text remains 16px and controls retain usable touch
targets. Normal and minimum-size Swing Mobile Management rendering is covered
by its existing preview test.

No open P0, P1 or P2 visual issues remain. P3: the bundled icon font has solid
glyphs in places where the illustrative mock used outlines. Existing Orders and
Inventory PNGs and the original Deckers header wordmark are reused directly.

Real iPhone standalone storage, installed certificates, browser local-network
permission, production gateway deployment and physical hardware are separate
rollout checks. No live store or installed application was changed by this QA.
