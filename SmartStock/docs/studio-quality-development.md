# SmartStudio quality improvements — development only

Added offline Fast (existing IS-Net) and Best quality (BiRefNet General) modes,
optional colour-guided edge refinement/background decontamination, and white,
black and checkerboard desktop result previews. Exports remain transparent PNGs
at input dimensions. Opaque interiors retain original decoded RGB; edge cleanup
may correct RGB in uncertain pixels to reduce colour spill. Existing source
transparency is respected. Erase/restore editing remains a future improvement.

Both models use 1024-square inference with their own documented normalization;
the existing model's expected resizing was retained. This is a model upgrade,
not an increase in input inference resolution. The Best ONNX file is
972,666,916 bytes and is pinned to SHA-256
`58f621f00f5d756097615970a88a791584600dcf7c45b18a0a6267535a1ebd3c`.
Its MD5 also matches the upstream rembg session's published checksum. Provenance
and license terms are in `tools/catalog-studio-model-NOTICE.txt` and
`tools/birefnet-LICENSE.txt`.

The authenticated LAN API accepts optional `quality` (`FAST`/`BEST`) and
`cleanEdges` fields. Missing fields preserve existing clients' Fast/uncleaned
behavior. Existing device, staff, permission and single-worker checks remain.
Server branding reports whether the Best model file is present; the desktop
defaults to Best when available and otherwise offers Fast. Missing or corrupt
models return an explicit 503 error; no cloud fallback or runtime download is
performed. Desktop studio requests allow five minutes instead of 90 seconds.

Future Windows/macOS packaging scripts stage both verified models and notices.
The additional model increases the future server download and uses substantial
native memory (about 2.9 GB process working set observed in one local run).
No packaging scripts were executed, versions were not advanced, no installer or
update archive was generated, and no release metadata was changed for this work.
Installed apps, live databases, services and router settings were not changed.

## Verification

- Full SmartStock/SmartStudio Maven reactor `test` run: 784 tests, zero failures
  or errors, 38 skipped. The optional model checks were also run separately with
  both pinned files: neither model check was skipped and both passed.
- Focused follow-up tests passed after the final edge-loop/UI status edits.
- Edge checks cover opaque RGB preservation, source transparency, coloured-halo
  reduction, thin foreground and internal holes. Missing/corrupt model and
  invalid-quality checks passed.
- Shared security check, `git diff --check`, PowerShell parser checks and Bash
  syntax checks passed. Actual Windows/macOS packaging remains deferred.
- Compiled desktop UI rendered at normal and minimum window sizes; checked old
  server Fast fallback, Best default with model availability, three preview
  backgrounds, unchanged export bytes and image-loading controls.
- Local comparison on the repository's apparel marketing image took 3.74 seconds
  for Fast without cleanup and 53.27 seconds for Best with cleanup. Best visibly
  preserved the dark shirt's opacity better. These are sample timings on this
  computer, not a general accuracy score or store-server performance guarantee.
  The isolated synthetic Best test took 42.3 seconds including model loading.

Comparison images and UI renderings are under ignored `target` directories.
`tools/CompareStudioQuality.java` can repeat the comparison using a supplied
image, both model paths and a private output directory. It runs against compiled
classes and dependencies, without packaging or connecting to the live server.

Remaining before release: review representative actual store product photos,
check installed updated clients and live HTTPS removal on the store server,
measure impact during normal store workloads, and verify the eventual Windows
update/archive and macOS packaging on their respective platforms.
