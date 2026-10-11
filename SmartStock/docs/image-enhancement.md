# CPU image enhancement and background removal

SmartStudio's Image Studio and the existing browser photo studio offer background
removal alone, 2×/4× enhancement alone, or removal followed by enhancement.
Original-size removal is the default. The original is preserved; a separate PNG
can be saved or copied. Desktop previews include Fit, 100%, and 200% zoom.

An administrator installs **Image enhancement · 2× / 4×** in **Status > AI Models**.
Then sign in again in SmartStudio or reload the browser to refresh availability.
This first implementation explicitly downloads a pinned upstream ONNX conversion
from Hugging Face, rather than changing Deckers' existing two-model catalogue.
Internet is needed only for installation/repair. Processing remains on the store
server; customer images are not sent to the model host or retained by this tool.
No application release, hosted catalogue, or installed store has been changed.

## Model and processing contract

- Real-ESRGAN x4plus, FP32 RRDBNet, 23 blocks. This is the first validated model,
  not a claim that it wins on every customer photo. GPU acceleration and additional
  model comparisons remain future work.
- Upstream weights: Real-ESRGAN v0.1.0 `RealESRGAN_x4plus.pth`.
- ONNX conversion and manifest:
  <https://huggingface.co/skillsafe-ai/realesrgan-x4plus/tree/107475e49b7d46412cc966481efccc3500955801>.
- Size: 67,051,650 bytes. SHA-256:
  `4851ec156207d271f5328605d0582eeb851e656227da8aca093ced9e60789291`.
- Opset 17. Float32 `input` `[1,3,H,W]`, RGB `[0,1]`; `output`
  `[1,3,4H,4W]`. 2× uses a single native 4× inference followed by 2×2 averaging,
  rather than a separate native 2× model or repeated enhancement passes.
- CPU inference uses two threads at most, 96-pixel source tile cores and 24-pixel
  context margins. Transparency is resampled separately. Nearby visible colour
  is extended into fully transparent pixels to reduce dark edge contamination.
- The existing single photo worker covers both stages. Removal finishes and its
  model session closes before enhancement begins. A failed request returns no
  partial output. A 25-minute tile-processing deadline and 30-minute desktop
  request timeout bound the operation; interactive server cancellation is not
  included in this version.
- Inputs retain the existing PNG/JPEG, 6 MB and 12-megapixel limits. Enhanced
  output is limited to 24 megapixels, with an additional server heap check.
  A 4× request therefore permits at most 1.5 megapixels of source image; 2×
  permits up to 6 megapixels. Use a smaller scale or crop if a limit is exceeded.
- Displayed print dimensions are pixel dimensions divided by 300 PPI; this is
  a sizing guide, not a quality guarantee or a change to printer settings.
  Review facial details, lettering, texture, colours and transparency before
  printing. Existing previews are not colour-managed print proofs.

## Storage, access and compatibility

Weights live at the active store profile's `ai-models/enhancement/<sha256>.onnx`,
outside app, update and rollback directories. Downloads use a filesystem lock,
bounded streaming, byte-size and SHA-256 checks and atomic activation. Invalid
files and incomplete downloads never become available. Model weights remain
excluded from application packages. The existing packaged studio notice now
contains Real-ESRGAN attribution and its complete BSD-3-Clause licence.

The authenticated LAN endpoint `/v1/studio/remove-background` gains optional
`removeBackground` (default `true`) and `scale` (default `1`, allowed `1/2/4`).
Enhance-only requires `2` or `4`. Catalogue `review-jpeg` requests retain their
existing behaviour and reject enhancement. Existing device approval and employee
permissions apply to both stages. The browser endpoint uses the same service,
staff permission checks and CSRF protection.

Model status adds an `enhancement` object beside the unchanged `models` array.
Installation uses `family: "enhancement"` on the existing administrator-only
model install endpoint. Branding adds `studioEnhancementAvailable`. Older servers
leave enhancement disabled in new clients; older clients retain removal defaults.

## Verification and rollout

Run the normal Maven/security/whitespace gates. For real offline model checks:

```powershell
mvn -q -f SmartStock/pom.xml test -Dtest=StudioImageEnhancerTest `
  -Dsmartstock.test.enhancementModel=<absolute-enhancement-model-path> `
  -Dsmartstock.test.photoModel=<absolute-IS-Net-model-path>
```

Checks cover both scales, source preservation, alpha, tile boundaries, combined
processing, enhance-only background retention, limits and corrupt/incomplete
installation. Real customer-photo comparison, representative large-image CPU
timings during store workloads, installed Windows/macOS apps, live LAN/browser
permissions and physical print quality remain separate rollout checks. Follow
the existing release packaging/publication guide before preparing a release.
