# Website shirt designer

The store owns shirt setup and pricing. Customers preview their design and submit
an estimated quote, without payment or stock reservation. Supabase's existing
authentication/private artwork storage and the existing Cloudflare gateway are
reused; PostgreSQL remains the live store authority.

## Staff setup

1. Open Custom Order Items. Check **Shirt designer** on the item form, or edit the
   **Shirt designer** column in the custom-item inventory list and save changes.
   **Shirts only** filters the list. Variants inherit the parent setting.
2. Use inventory product type and fixed pricing. Set a base price on the item, or
   active size/color variants with their own fixed prices.
3. Open **Shirt designer setup**. Enter each variant's exact color name and its
   `#RRGGBB` preview swatch, plus the available vinyl ink swatches.
   Select **T-shirt** with **Round neck** or **V-neck**, or **Collared shirt**.
   Each custom item represents one style; sizes/colors remain its variants. The
   inventory list displays the inherited shirt style. Built-in mockups switch
   with the chosen style; custom photo paths are preserved. Existing configurations
   default to round-neck T-shirts. Collared built-in mockups use a polo collar.
4. Configure physical print widths/heights in inches and each print rectangle as
   x/y/width/height fractions of the square mockup. Chest means the wearer's left
   chest (the right side of a front-facing preview).
5. Customer add-ons automatically include every active priced preset under every
   active print material. Vinyl, transfer paper, embroidery, and newly created
   materials all use the same catalogue lookup; no material-name allowlist or
   separate website checklist exists. Both `FIXED_PRESET` and `PER_LINE` prices
   come from the store. New options appear on the next catalogue refresh.
   Fixed-price presets support text or a logo; per-line presets add one editable
   text line at a time, charged separately. Legacy per-area price mappings are
   not needed for this automatic catalogue. Saving shirt setup enables automatic
   options; configurations default to this mode when the setting is absent.
6. Enable the shirt on the website and save the item. The **Shirt setup** column
   reports incomplete setup. Inactive, archived, unchecked, unpublished, unpriced,
   or incompletely configured shirts never appear in the website shirt catalogue.
   A variant needs a configured color swatch to appear.

The default grayscale front/back mockups retain transparency and fabric shading.
Custom mockup files can be added to `website/public/` (for example
`shirt-mockups/style-front.png`), then assigned as
`/shop/shirt-mockups/style-front.png`. Calibrate the rectangles for that photo and
rebuild the website bundle. External image URLs are not accepted.

## Customers and estimates

Customers enter through Custom T-Shirts or a custom shirt listing. Other apparel keeps its existing quote workflow.
Customers choose the shirt type and, for T-shirts, the neckline before selecting
the item, sizes, and colors. Only configured, published styles can be selected.
The designer offers text, PNG/JPEG layers, dragging, size/position/rotation
controls, layer selection/deletion, undo/redo, and front/chest/back previews.
Customers select a material and preset before adding text or uploading a logo. An uploaded
logo is immediately placed on the selected area; uploaded images can be reused.
Each new text line starts below the previous artwork and has its own text and
position. Customers can add the same preset repeatedly, delete individual
elements, and change an element to another compatible preset. Preview dimensions
are displayed in inches. Existing explicit per-preset sizes and print methods
are preserved and enforced during estimation/submission. The native preset
catalogue otherwise stores names and prices, without structured physical sizes
or print-color methods: those options use editable preview sizes and artwork
colors, with final sizing and production confirmed by staff. The designer never
invents dimensions or infers a production method from a material's name.
One-color artwork can use a separate configured ink swatch for each element.
Full-color logos and single-color text can coexist on the same side.
Explicit legacy configurations with `useSystemPresets=false` retain their
previous per-area or curated-preset pricing; opening and saving setup switches
to the automatic catalogue.
Single-color mode flattens the affected artwork to one configured vinyl color;
opaque image backgrounds also become vinyl. Staff checks cutting suitability.
PDFs are private reference attachments and cannot be placed on a shirt.

One design applies to one shirt style and up to 50 size/color quantity groups,
with at most 1,000 shirts total. Each area supports 20 layers, with 40 total.
Uploads retain the existing five-file/4 MB limits. Images must decode and be no
larger than 8,192 pixels per side or 24 megapixels. A draft, including artwork,
stays in this browser's IndexedDB through sign-in; successful submission clears it.

`shirt-estimate` recalculates from current store catalogue/presets, and submission
and finalization recalculate again. Changed pricing requires customer confirmation.
Unknown, inactive, removed, incompatible, and explicitly oversized add-ons cannot
be quoted. Each per-line text element must contain exactly one line; each charge
and imported order add-on retains its native `FIXED_PRESET` or `PER_LINE` mode.
If customer add-ons are enabled after a legacy browser draft was saved, select
each old layer and assign an **Element add-on** before submitting. Original text
and uploaded files remain in the browser. Staff review and imported order add-ons
retain each selected preset, charge, and text-line content separately.
Estimates follow native custom-order whole-currency prices and final-order-total rounding rules;
normal-POS VAT is not added again. Customer-specific discounts and staff adjustments
are confirmed in the final quote.

## Private review and production

Quote creation stages the design as `AWAITING_ARTWORK`; `shirt-finalize` verifies
that every placed image belongs to that customer, request, and store before making
it visible to staff. Retries use the same request and immutable file identifiers.
Completed shirt designs cannot receive replacement uploads.

Structured version-1 design data, the catalogue/mockup configuration at submission,
and the store-calculated estimate are saved inside the private quote context.
They never enter public snapshots. Existing generic quote requests remain compatible.
New design documents also retain the selected type/neckline key. If staff changes
that classification while a customer is designing, the store requires a refresh
and review before submission instead of silently substituting a different shirt.

Company Preferences → Custom requests → **View request** shows private front/back
previews, grouped quantities, material/preset charges, and instructions.
**Import shirt order** opens an editable Custom Orders draft for the linked store
customer and carries quantities, print add-ons, originals, and preview PNGs. Staff
confirms final prices, phone, due date, and deposit before saving. Existing permissions,
deposit/price approvals, and proof approval gates apply. The saved order is linked
back to its structured website request. If linking fails, the order remains saved;
use **Link SmartStock order** to retry without creating another order.

## Installation and validation

The local baseline and ordered `20261013120000_shirt_designer.sql` migration add
the parent flag/configuration and the partial shirt catalogue index. The schema
contract applies the upgrade during ordinary server provisioning. Existing generic
LAN reference serialization and recovery manifests carry the added item columns;
registers do not receive server credentials. The storefront schema remains v15.

Checks:

```powershell
pnpm --dir SmartStock/website build
pnpm --dir SmartStock/website test
pnpm --dir SmartStock/cloudflare/smartstock-storefront test
mvn -q -f SmartStock/pom.xml test
git diff --check
```

Run `SmartStock/tools/security-check.sh` through Git Bash. Browser acceptance is
`node SmartStock/website/test/shirt-browser.mjs`; it requires Playwright with Edge
and uses synthetic APIs only. `CODEX_NODE_MODULES` can point to the bundled Node
modules directory. It saves desktop/mobile screenshots under `output/shirt-designer`.
The database integration test only accepts an explicitly selected disposable cluster
at `jdbc:postgresql://127.0.0.1:55449/postgres`, user `shirt_test`, via
`-Dshirt.test.admin=...`; it never reads the live application's connection settings.

Preparing the bundle does not publish or install it. Before public enablement,
verify installed-server migration/service behavior, the storefront tunnel and actual
website/gateway deployment, real private uploads/proofs and customer-to-order linking,
and a physical shirt sample using the chosen dimensions and material presets.

Customer preset acceptance: `node SmartStock/website/test/shirt-addons-browser.mjs`
uses the same Playwright/Edge setup with synthetic APIs, and writes screenshots
under `output/shirt-addons`. It covers direct logo placement, repeated text lines,
per-element vinyl colors, delete/undo, draft reload, mixed print methods, mobile
layout, embroidery, newly added material/preset discovery after refresh, and quote
submission. `ShirtAddOnTest` covers authoritative prices, repeat charges, automatic
catalogues, per-line validation, size limits, and configuration validation. The
opt-in database test also checks discovery of newly inserted presets and removal
of inactive presets/materials against its disposable database only.

## Mockup provenance

The built-in ImageGen tool generated `website/public/shirt-front.png` and
`website/public/shirt-back.png`. Prompts requested a photorealistic neutral-gray
blank cotton crew-neck shirt, front/back respectively, straight-on flat-lay product
photography, subtle grayscale fabric folds, a smooth blank torso, and a transparent
background, with no text, logos, mannequin, props, or exterior shadows. These are
illustrative previews, not photographs of stocked garments. Bundled Noto Sans,
Noto Serif, and Roboto Mono fonts retain their SIL Open Font License notices.

Front prompt: "Create one production asset: photorealistic neutral light-gray blank
crew neck cotton T-shirt FRONT view, flat laid straight, symmetric, straight-on
orthographic product photography. Entire shirt occupies x 8%-92%, y 5%-95% of a
square canvas. Gentle realistic fabric folds and seams, large smooth blank torso
for artwork overlays. No logos, text, mannequin, hanger, props or shadows outside
garment. Transparent background, preserve silhouette alpha. For web shirt designer
tinting; neutral grayscale fabric and diffuse shading."

Back prompt: "Production web mockup asset: photorealistic neutral light-gray blank
cotton crew neck T-shirt BACK view, flat laid straight and symmetric, straight-on
orthographic product photography. Entire garment occupies x 8%-92%, y 5%-95% of a
square canvas. Same conventional short-sleeve shirt as a front mockup: straight
hem, large smooth torso, subtle natural fabric folds, seams and diffuse grayscale
shading for color tinting. Back view has a high back neck. No text, logos, mannequin,
props or outside shadows. Genuinely transparent background."

Style mockups were also created with the built-in ImageGen editor and saved as
`website/public/shirt-vneck-front.png`, `website/public/shirt-collared-front.png`,
and `website/public/shirt-collared-back.png`. The user confirmed that collared shirts
are polo shirts with a short button placket. V-neck tees reuse the standard tee back.

V-neck prompt: "Edit this FRONT flat-lay blank shirt mockup to be a V-NECK T-SHIRT.
Replace only the round neckline with a natural moderately deep V-shaped ribbed
neckline. Preserve exactly the body and sleeve silhouette, placement and scale on
the square canvas, neutral grayscale fabric texture and shading, smooth printable
torso, and transparent background. Photorealistic cotton garment. No lettering,
branding, buttons, hanger, person or extra objects. This asset must align with the
existing shirt designer print-area coordinates."

Polo front prompt: "Edit this blank FRONT shirt mockup into a standard short-sleeve
POLO COLLARED SHIRT. Add a realistic folded polo collar and a short centered
three-button placket at the neck, with no buttons elsewhere. Preserve the body's
and sleeves' exact silhouette, scale and placement on this square canvas, neutral
grayscale cotton fabric texture, shading and folds, and a smooth blank printable
torso. Keep a genuinely transparent background. Photorealistic flat-lay garment,
no logos, lettering, hanger, person, or extra objects. Must align with existing
shirt-designer print-area coordinates."

Polo back prompt: "Edit this blank BACK shirt mockup into the BACK VIEW of a standard
short-sleeve POLO COLLARED SHIRT. Add a realistic folded polo collar visible around
the back neck. No buttons or placket visible from the back. Preserve the exact body
and sleeve silhouette, scale and placement on the square canvas, neutral grayscale
cotton fabric texture, folds and diffuse shading, smooth blank printable torso, and
genuinely transparent background. Photorealistic flat-lay garment, no text, logos,
hanger, person, or extra objects. Must align with existing shirt-designer print-area
coordinates."

## 3D preview and simplified setup

The storefront lazy-loads a local Three.js garment preview with front/back cameras, orbit and zoom, live color swatches, and front/chest/back artwork textures. Round-neck uses the Blender-authored garment from the MIT-licensed Poimandres T-shirt configurator (Anderson Mancini / Poimandres), with natural cloth folds, continuous sleeves, baked occlusion, and fabric normals. V-neck and polo are derived meshes; the latter adds folded collar leaves, a back collar band and button placket. Rebuild the derivatives with `node tools/shirt-model-variants.mjs`. Models and the upstream MIT notice are bundled in `public/assets/shirts/`, so runtime needs no external service. Normal and dry-fit fabric use distinct surface finishes. These are generic garment previews, not scans of individual stock items. Artwork uses separate front/back projections that follow cloth folds. Model bytes are cached, and artwork/color updates retain the model and camera.

Inventory uses the **This is a shirt** checkbox. Shirt setup has **Enable designer on website**, T-shirt/polo, and normal/dry-fit choices; neckline is hidden for polos. Print areas, prices, vinyl and garment color swatches are in the expandable section. The automatic add-on flow uses the existing active materials/presets and inventory size/color values. Enabling it does not require duplicate print dimensions, price mappings, or color swatches. Optional swatches refine preview colors; recognized inventory color names receive an approximate preview color, and other names use a neutral preview. Prices are never inferred from material names. Explicit legacy mappings still require valid configuration. Existing settings default to normal fabric. Bundled mockups remain the precise 2D artwork editor and production review preview. WebGL failure offers that editor directly.

Validation: `pnpm build`, `pnpm test`, `node test/shirt-browser.mjs`, and targeted Maven shirt tests. Browser acceptance uses a synthetic catalog, not the live store.
