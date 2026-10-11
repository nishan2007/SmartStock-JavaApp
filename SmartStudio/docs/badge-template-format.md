# Creating editable SmartStudio badge templates

This file is for ChatGPT or another conversion assistant. The operator setup
guide is `badge-setup-windows.md`. No API key or AI integration inside SmartStudio
is required: author portable `.sstbadge` files locally and open them in SmartStudio.

## Folder contract

All final badge designs are direct children of the chosen **Badge Templates**
folder, with `.sstbadge` extensions. Do not create one subfolder per school.
Use readable names such as `Skeldon Primary School.sstbadge`. Artwork is embedded;
the template never depends on loose image files or another computer's paths.
Keep source exports, extracted images, previews, reports and candidates in the
separate local **Asure ID Conversion** workspace. Only reviewed reusable designs
go into the library; personal photographs and issued badge data do not.

## Minimal example

```json
{
  "formatVersion": 1,
  "name": "School badge",
  "widthMm": 85.6,
  "heightMm": 54,
  "elements": [
    {
      "label": "Name",
      "type": "TEXT",
      "text": "Full name",
      "field": "fullName",
      "font": "Arial",
      "fontMm": 3,
      "bold": true,
      "color": "#000000",
      "image": "",
      "x": 5,
      "y": 40,
      "width": 45,
      "height": 6
    },
    {
      "label": "Portrait",
      "type": "PHOTO",
      "text": "",
      "field": "",
      "font": "Arial",
      "fontMm": 3,
      "bold": false,
      "color": "#000000",
      "image": "",
      "x": 5,
      "y": 10,
      "width": 18,
      "height": 24
    }
  ]
}
```

## Rules that the assistant must follow

- UTF-8 JSON, `formatVersion: 1`, meaningful name (1-160 characters), 10-300 mm
  width/height, at most 64 layers, and total file size at most 8 MB.
- Layers paint in array order, bottom to top. Positions use the card's top-left
  origin. All lengths, positions and font sizes use millimetres. Every layer must
  fit within the card. Width/height must be positive; numbers must be finite.
- `TEXT`: static text has an empty `field`. Variable text uses a unique reusable
  key such as `fullName`, `grade`, `phone`, `employeeNumber`, or `department`.
  Keys match `[A-Za-z][A-Za-z0-9_]{0,39}`. SmartStudio automatically shows an entry
  box for every distinct text key, labeled by its layer name. Repeated keys share
  the same value. `text` is a safe generic placeholder, never a real issued value.
- Text is horizontally centered, vertically centered, clipped to its rectangle,
  and reduced to fit the width. `font` is an installed font family; `fontMm` is
  0.5-50 mm. `bold` is boolean; colour is a six-digit hex string such as `#000000`.
  Match the original font where installed; report substitutions in the review log.
- `IMAGE`: put raw base64-encoded PNG/JPEG bytes in `image`, with no `data:` prefix.
  Images are stretched to the specified rectangle, preserving the printed
  reference geometry. Each decoded image is limited to 12 megapixels. Use only
  reusable logos/backgrounds. Transparent PNG is supported.
- `PHOTO`: use empty `image`, `field`, and `text`. The photograph is entered at
  production time, cropped to fill the rectangle, and never stored in the design.
  The current workspace uses one session photo for every PHOTO layer. Flag designs
  requiring different photographs in one card rather than silently substituting.
- Include every example element property explicitly in generated files. Do not
  encode external database connections, credentials, scripts or personal records.
- This version renders one side per template. Preserve a two-sided original as
  separate `Name - Front.sstbadge` and `Name - Back.sstbadge` files and report the
  pairing. Do not claim that duplex alignment or automatic front/back printing is
  implemented.
- Dynamic barcodes, NFC/magnetic encoding, signature capture, rotations and other
  unsupported design functions require a **needs-review** status. A static barcode
  reference image is not a replacement for a working variable barcode.

## Recovering geometry from an Asure ID print PDF

Render the PDF and identify the card rectangle; exclude the printer's page margins.
PDF pages may be landscape Letter while the card itself is only 85.6 x 54 mm.
Confirm the card dimensions in Asure ID; do not treat the paper dimensions as
the badge dimensions. For a card rectangle `(left, top, cardWidth, cardHeight)`
and a layer rectangle `(x, y, width, height)` in top-origin PDF coordinates:

```text
xMm      = (x - left) / cardWidth * cardPhysicalWidthMm
yMm      = (y - top) / cardHeight * cardPhysicalHeightMm
widthMm  = width / cardWidth * cardPhysicalWidthMm
heightMm = height / cardHeight * cardPhysicalHeightMm
```

Extract reusable image objects from the PDF when possible. Replace text baked into small images
with editable TEXT layers. A flattened whole-card image is only a reference,
not an editable conversion. Remove real names/photos from any recovered background
before including it, or rebuild the background cleanly. Compare the complete
rendered card to the source before calling conversion successful.

## Validation without clicking through the editor

The installed build includes `gy.deckers.studio.BadgeTemplateTool`. The setup
kit's `Invoke-BadgeTemplateTool.ps1` wrapper uses the app's bundled Java runtime:

```powershell
.\Invoke-BadgeTemplateTool.ps1 -Action validate -InputPath 'C:\Badge Templates\School.sstbadge'
.\Invoke-BadgeTemplateTool.ps1 -Action preview -InputPath 'C:\Badge Templates\School.sstbadge' -OutputPath 'C:\Asure ID Conversion\Previews\school.png'
.\Invoke-BadgeTemplateTool.ps1 -Action check-library -InputPath 'C:\Badge Templates'
```

Use the actual chosen folder paths. If the app is installed elsewhere, pass
`-StudioPath`. The validator checks structure, geometry, images and rendering;
it does not prove visual similarity, physical printing or synchronization. The
preview command refuses to overwrite an existing PNG: use numbered review files.
