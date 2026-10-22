# Canvas workspace

SmartStudio no longer logs out because of inactivity. The matching store-server
session policy exempts authenticated SmartStudio sessions from idle and absolute
session timeouts; registers retain their existing limits. Manual sign-out,
closing the application, session revocation, blocked devices and inactive users
still end or deny access. Both the SmartStudio client and store-server update
are required to remove both timeout sources; an older server still enforces its
15-minute idle / 12-hour absolute limits. This does not persist sign-in across
application restarts.

SmartStudio opens a workspace chooser after sign-in. Badge Cards offers a
searchable template preview picker with the current design, the built-in school badge,
a blank badge, saved library files, and file import. Business Cards offers the
current design, a new card, or an existing Publisher template. Change template /
workspace returns to this chooser without discarding the open editors.

Type part of a template name into Search templates to filter the list. Search
ignores capitalization and matches all typed words. Selecting a result updates
the preview; Open selected opens that exact choice. No matches clears the
selection and disables Open selected. Choose template folder changes the local
library location from inside the picker; search does not scan other folders.

The canvas is the main view. No template gallery is permanently visible.
Template and output controls are grouped in menus. Account contains updates
and sign-out. Existing Deckers components and palette remain in use.

## Editing

- Badge side panels: Details, Properties, Insert, and Layers.
- Business-card side panels: Layers, Properties, Data, and Print setup.
- Side panels start closed for card editing. Click a rail button to open a
  panel; click it again or Hide panel to recover the canvas space.
- Select an object on the canvas or in Layers, then open Properties. Enter or
  leaving a property field applies its value through the existing validated
  editor model. Layers retains the advanced table editor.
- Clicking an empty canvas area clears object selection. Undo or model changes
  invalidate stale property editors.
- Badge undo/redo, dragging, fine keyboard movement, and zoom remain available.
  Business cards retain their existing editing capabilities; this layout does
  not introduce business-card undo/redo.
- Fit uses a tighter page-focused pasteboard. Off-page objects still expand
  its bounds and remain editable. Canvas sizing does not alter output units.

## Images and output

Image Studio has Original, Result, and Compare views. Choose/Paste remain above
the preview; processing options live in the Image tools panel. Output contains
PNG saving, clipboard copying, and printing. Image tools can be hidden while
inspecting the image. Processing continues through the existing store API.

Business-card merge records and Excel controls are in Data; sheet preview,
paper setup, quantities, and output are in Print setup. Badge front/back and
Magicard controls remain in Output. Business-card output remains single-sided.

Template replacement keeps each editor's existing unsaved-change safeguards.
Badge replacement/close additionally offers Save template, Discard changes,
or Cancel; failed saves block replacement. Business-card save remains an
explicit template action and its existing discard confirmation remains in use.

This implementation reorganizes the existing editors. It does not add new
template formats, business-card history, or a direct Apply processed image to
template operation. Existing clipboard/file photo transfer remains available.

## Verification

Run `mvn -q -f SmartStudio/pom.xml test` from the repository root. Layout tests
render badge canvas and panel states at 820 and 1100 pixels, check that a closed
inspector leaves the canvas dominant, and verify property edits and stale-field
rejection. Existing tests cover drag/history, zoom, image controls/transfer,
card dimensions, Excel, packing, and exports.

Also run the shared Publisher rendering/packing and card architecture tests,
the repository security script through Bash, and `git diff --check`.

Before deployment, check the installed application at Windows scaling levels,
sign-in against a real store, server processing/progress/cancellation, saved
template imports, menus and dialogs, and actual printing. Verify physical badge
and business-card dimensions, sheet cuts, and Magicard manual flips on the
target printer. No release or live installation is part of this UI change.
