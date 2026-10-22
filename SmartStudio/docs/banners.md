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

Banner PNG/JPEG insertion and paste accept **100 MiB per image**, up to **100
megapixels**. File size and decoded pixel count are separate limits. Original
encoded bytes are preserved for save/reopen and export; the preview does not
replace the source. Canvas previews use source subsampling to at most 2,048
pixels per side; library previews use at most 1,200. AI background processing
retains its separate 6 MiB / 12 MP input limit.

**Local banner templates support up to 5 GiB** for both the archive file and
its expanded contents, including metadata. Design JSON is capped at 16 MiB;
individual assets are capped at 100 MiB. Archive overhead counts toward the
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
dimensions and print at actual size / 100%. **Print** explains this workflow
rather than sending oversized banners to an ordinary desktop sheet setup.
Roll width, maximum job length, tiling, finishing, colour profiles and printer
margins remain printer/RIP-specific physical acceptance steps.

## Verification

Checks cover 4 x 20 ft, both orientations of 10 x 100 ft and 30 x 300 ft,
create/edit/save/reopen, PDF dimensions, original photo pixels, gradients,
undo/redo, clipboard references, pan, byte limits and viewport bounds. Exact
100 MiB artwork is tested with a valid PNG boundary fixture. Invalid archive
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
