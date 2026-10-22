# SmartStudio 0.1.11 Windows

Build 1011 fixes badge template dragging stopping after the first movement.
Updating layer coordinates now preserves the selected table row, and a drag
keeps its captured layer until the mouse is released. Clicking a canvas object
focuses the editor for arrow-key movement: 0.1 mm normally and 1 mm with Shift.
Keyboard navigation in text fields and the layer table remains unchanged.
The pasteboard fits its viewport so the entire card is visible without internal
scrollbars. The viewport transform remains stable during dragging.

Templates support filled rectangles, ellipses, triangles and horizontal lines.
Size, position and colour are editable in the layer table. Bring forward and
Send backward arrange shapes around text, photos and artwork. Shapes remain
editable after saving and appear in preview, PNG/PDF output and printing.
See [template editing](template-editing.md).

All 33 Maven tests pass, including twenty consecutive drag updates through the
real panel/table callbacks, keyboard movement, selection preservation, shape
save/render/layer ordering and viewport sizing. The final installer was extracted;
its Studio and SmartStock 1.1.25 JARs match the tested Maven artifacts. The native
launcher and exact packaged Java runtime pass startup and shape/keyboard/project
checks with an isolated user home. Editor layout was visually inspected.
No model weights are bundled.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Installer | 154072419 | 18643a8e429736110feeb2e5a8f31d8129f7c5ab41d91dd04eca60c391e68376 |
| Portable ZIP | 156441533 | 4d394d5bf6a3346bf5c2f7734e735e3df2df958b132d336f5a0cb93926e9fc88 |

Actual installed-app upgrade/setup, macOS and physical card printing remain
separate checks. No running Studio installation was changed.

The complete hosted installer matched its exact local size and SHA-256 before
release metadata was published. Metadata readback matched version, build,
platform, path, size, hash and published status. The protected portal selection
was deployed with the verified installer. Public HEAD returns HTTP 200, the
0.1.11 filename and 154072419 bytes.

Download: https://downloads.deckers.gy/download/smartstudio/windows
