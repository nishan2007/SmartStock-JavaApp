# Banners workspace

Choose **Banners** from SmartStudio's workspace chooser. **New banner** accepts
width and height in FT, IN, CM or MM. Geometry is stored in millimetres. Use
**Template size** to resize an existing design, optionally scaling artwork.
Sizes must be positive and finite, up to 1,000,000 mm per side.

The workspace reuses the layer editor, text/WordArt, images, barcodes, clipboard,
undo/redo, multi-page designs and portable .sspub templates. Picture controls
accept banner dimensions; font controls allow up to 10,000 points. Layer
properties use millimetres. **Fit**, **+**, **-**, Ctrl+wheel, scrollbars and
middle-button drag provide fitting, zooming and panning. The canvas and page
thumbnails never allocate a full-print-size bitmap.

Banner drag handles and movement support the same 1,000,000 mm geometry limit.
Artwork can extend beyond the page for bleed and cropping. Disable **Lock aspect
ratio** to stretch width and height independently; leave it enabled to preserve
the original proportions.

## High-quality artwork and large templates

Banner PNG/JPEG insertion and paste have **no 100 MiB file cap**, up to **100
megapixels**. File size and decoded pixel count are separate limits. Original
encoded bytes are preserved for save/reopen and export; the preview does not
replace the source. Canvas previews use source subsampling to at most 2,048
pixels per side; library previews use at most 1,200. AI background processing
retains its separate 6 MiB / 12 MP input limit.

**Local banner templates support up to 5 GiB** for both the archive file and
its expanded contents, including metadata. Design JSON is capped at 16 MiB;
individual assets share that total budget. Archive overhead counts toward the
file-size limit, so leave a little headroom below the exact 5 GiB boundary.

Large .sspub files use ZIP64. Save/open stream one asset at a time with small
buffers. Original image data is stored in an immutable content-addressed
`banner-asset-cache` folder in the active Studio user home; publication and undo
snapshots retain file references rather than copying gigabytes of Base64 data.
The .sspub archive embeds all source assets, so it remains portable: reopening
on another computer recreates that computer's local cache. Copy/paste retains
asset references while the app is open. Do not delete the cache while editing
or holding copied layers. With Studio closed and all work saved, the cache can
be rebuilt by reopening the saved templates.

**Save**, Ctrl+S and **Save to template library** use the separate local
`template-library/banners` folder. **Export template file** writes a portable
.sspub to a chosen location. These large-template limits are local to Banners;
server-shared card/label libraries retain their existing limits. Older Studio
versions cannot open templates beyond their old size limits.

Allow disk space for the saved template, its cached source assets and temporary
save/export files. Saving over an existing 5 GiB template may require roughly
another 5 GiB for the atomic replacement, in addition to its asset cache. PDF
scratch space can be larger than the template. File I/O and checksums take time
at these sizes; loading/saving runs in background workers. ZIP64 files require
a filesystem that supports files over 4 GiB (for example NTFS or exFAT, not
FAT32). Source files remain preserved even when an oversized or cancelled save
is rejected; completed cache entries may remain for reuse.

## Export and print limits

**Export PDF** emits one actual-size page per printable design page. Text,
shapes and supported gradients remain vector artwork. Photos keep source pixel
dimensions and are encoded losslessly. PDF content streams use disk-backed
scratch storage; full-resolution decoded source images are processed as needed
instead of retaining every photograph as a page-sized bitmap. Pixel decoding
still needs working memory for an individual image, which is why the 100 MP
limit remains. A big physical canvas cannot add detail to a small source photo.

Pages over 200 inches use PDF 1.6 **UserUnit** to preserve full physical size.
Confirm that the print shop's reader/RIP honours UserUnit, then verify the page
dimensions and print at actual size / 100%. **Print** also sends actual-size
banner pages directly through the selected printer driver. On Windows, choose
the banner printer, confirm the automatically sized **Custom Size** page in
Page Setup, then select paper/quality options in the print dialog. Banner
dimensions are passed to the driver automatically; no typing or saved preset
is required. Studio uses the native print dialog to retain the driver's temporary
custom size. If Java's media list reports a default sheet instead of the temporary
custom size, Studio reads the native print job's page dimensions and removes
stale standard-media attributes. Paper that is genuinely too small is rejected.
Roll width, maximum job length, tiling, finishing, colour profiles and printer
margins remain printer/RIP-specific physical acceptance steps.

Windows banner jobs retain the native driver's resolution rather than replaying
the resolution attribute copied out of the print dialog. This avoids a stale
resolution overriding the device transform after custom paper setup. Direct
banner jobs also record page dimensions and raster transforms locally; use
**Copy print details** after sending a job when investigating driver scaling.
The 44 x 72 inch regression checks all four corners through banded rendering;
GP-4600S driver preview and physical output still require device verification.

## Verification

Checks cover 4 x 20 ft, both orientations of 10 x 100 ft and 30 x 300 ft,
create/edit/save/reopen, PDF dimensions, original photo pixels, gradients,
undo/redo, clipboard references, pan, byte limits and viewport bounds. Exact
Artwork above 100 MiB is tested with a valid 101 MiB PNG fixture. Invalid archive
paths, decompression limits, preservation of existing saves and legacy-library
limits are tested separately.

A real **5,348,560,698-byte ZIP64 template** with 51 x 100 MiB assets was saved
and reopened with a **256 MiB heap**. Asset contents and offsets beyond 4 GiB
were verified. The storage proof uses binary fixtures rather than claiming
5 GiB of rendered image content or physical printing was tested.

```
mvn -q -f SmartStock/pom.xml -Dtest=PublicationLargeFilesTest -Dsmartstudio.largeTemplateProof=true -DargLine=-Xmx256m test
mvn -q -f SmartStudio/pom.xml -Dtest=BannerDocumentsTest,BannerArtworkTest -Dsmartstudio.bannerProofs=true -DargLine=-Xmx512m test
```

The opt-in storage proof writes over 5 GiB temporarily; allow at least 8 GiB of
free test disk space. Bounded PDF proofs and Swing rendering are in
`SmartStudio/target/banner-validation` and are visually inspected. Installed-app,
macOS, large-format driver/RIP and physical printer verification remain separate.
This source change does not publish or install a release.

Low-resolution banner printing and PDF export offer resampling up to 300 PPI.
The raster budget scales with the JVM heap (up to 100 megapixels). Generated PNGs are streamed to temporary disk files without a 100 MiB encoded-file cap. Larger
banners, including 4 x 6 ft and larger, use a lower output PPI when necessary
without changing physical dimensions. The confirmation lists each image's
output PPI. Images already larger than the budget retain their source pixels;
other images can still be resampled. Resampling smooths enlargement but does
not recover missing source detail. The original template assets stay unchanged.

Resampling performance keeps the same bicubic pixels, alpha and output PPI:

- A session cache reuses unchanged source bytes, crop and output dimensions
  across print/export jobs. It holds at most 16 lossless PNGs or 512 MiB on
  temporary disk; restarting SmartStudio starts a new cache. Each job owns a
  separate temporary file, so completing one job cannot invalidate another.
- Independent source images can run on up to four workers. Worker count is
  limited by CPU count and a conservative source/output memory estimate.
  Repeated uses of one source stay together to share decoding and scaling.
- Full-resolution output can consume the prepared pixels directly. Java may
  reclaim those soft references under memory pressure; output then reads the
  same lossless PNG. The references are never saved in template archives.
- Application logs include hashing, decoding, scaling, encoding, cache hits,
  worker count and PDF export duration. These distinguish resampling time from
  PDF generation or printer-driver delays.

Validation includes pixel comparisons for RGB/alpha crops and independent
images, cache invalidation when source bytes change, independent job cleanup,
and renderer fallback after prepared pixels are reclaimed. A local synthetic
three-image job (each enlarged from 500 × 350 to 2100 × 1470, 768 MiB heap)
took 637 ms on its first resampling pass and 28 ms on its cached repeat.
PDF generation took another 2039 ms and 1761 ms respectively; these are local
measurements, not a guarantee for a particular banner or printer. Installed-app
and physical printer checks remain separate; this change does not install or
publish an application release.
