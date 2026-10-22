# SmartStudio 0.1.20 — local Publisher import repair

Build 1020 repairs Business Cards import using actual local Publisher files.
LibreOffice is detected in common installations, on PATH, or in the local
Tools converter directory; a manually chosen executable is remembered.
Custom and portrait template sizes are retained. Incomplete editable
reconstructions are replaced by their complete rendered artwork exactly once,
with unused reconstruction assets removed. Unsupported embedded image formats
now trigger the complete-artwork fallback instead of aborting import. Small
reference pages are rendered at 300 DPI. Conversion subprocesses skip printer
discovery to avoid offline printer delays.

Fresh cards no longer appear modified merely because the default paper was
selected during initialization. The conversion notice wraps within the screen.

## Validation

- All 62 SmartStudio tests passed; the final notice-only change was packaged
  afterward with tests skipped.
- Targeted Publisher tests passed: 22 existing Publication tests and one new
  unsupported embedded artwork regression.
- Security and whitespace checks passed.
- All 69 local `.pub` files converted to 69 `.sspub` templates and 99 design
  pages. Installed-code verification reopened all archives, rendered all sheets,
  and checked/corrected every PDF's page count: 99 PDF sheets in total.
- All 99 pages used complete-artwork fallback. Original text is therefore part
  of the image, with editable new text/images supported above it.
- Native UI verification using the installed application's classes covered
  automatic converter selection, the formerly failing custom-size import,
  displayed physical dimensions, and full-sheet print preview.
- The final installed executable, config, application JAR and shared dependency
  match their package inputs. The actual installed launcher passed its startup
  check. Application JARs contain no ONNX model weights; the inference runtime
  remains included.

The local per-user SmartStudio application was updated and reopened. Its former
application files were backed up outside the repository first. SmartStock's two
existing processes remained running throughout. No release was published and
no store database or service was changed.

## Final local artifacts

- Portable ZIP: `SmartStudio-0.1.20-windows-20261016-123403.zip`
- ZIP size: 234034261 bytes
- ZIP SHA-256: `456752b0d0a104597109234adef39a6d68e014dfd6e04c5f5414657ce02d487a`
- Installed application JAR SHA-256:
  `985ed87c5500dfa89f76613dca58cadef94d82da3de3d5a1c645fe97b375966c`

## Remaining checks

Physical printer output, actual-size alignment, paper cutting and comparison of
every converted font against Microsoft Publisher remain manual checks. The
folder contains ticket/form designs as well as business cards; their original
dimensions were intentionally retained. macOS packaging was not performed.
