# Badge Cards: Skeldon Primary reconstruction

The Badge Cards section includes an editable reconstruction of the Asure ID
`print topdf.pdf` design. The `.card` file was not used. Native `.xtp` decoding
has not been implemented: the supplied files contain an opaque binary format.

The portable `.sstbadge` format contains a versioned JSON design with embedded
reusable artwork and physical dimensions in millimetres. The initial card uses
85.6 × 54 mm, the standard landscape badge size; verify actual-size printing on
the target printer. The template retains the background and logo extracted from
the PDF, with editable school heading, motto, pupil name, grade, contact phone,
and a photo placeholder. Original pupil details and photograph are excluded.

After staff sign-in, choose **Badge Cards**. Double-click design cells to adjust
content, font, field binding, colour, and geometry. Text fits its available width.
Every distinct text field binding gets its own labeled entry box, including
`fullName`, `grade`, `phone`, and custom names. Photo fields
use the chosen session photo, cropped to fit. Template edits support adding text
or photo fields, removing layers, and replacing existing reusable artwork.

**Template folder** selects one persistent library folder; **Templates** lists
its `.sstbadge` files. **Save template** saves directly into that folder. An
external import is copied there only when saved. **Open / import** and **Export
template** read/write `.sstbadge` files, including embedded artwork. Pupil data
and photos are session-only, clear at sign-out, and do not enter template files.
PNG and single-page PDF exports use 300 DPI; PDF page dimensions match the card.
Those production outputs default to local `Documents/SmartStudio/Badge Output`,
not the shared template folder.
Templates are limited to 8 MB, 64 layers, and 12-megapixel artwork.

Users may select the same shared OneDrive folder on each computer; Windows
handles file synchronization. An externally changed local template is detected
before ordinary saving so its edits are not silently overwritten. This is not
server-managed synchronization and cannot detect undelivered remote changes.

This first section does not yet implement server-managed cross-store OneDrive synchronization,
server-managed template permissions/revisions, persisted badge records, batch
record import/output, back-side editing, or native Asure ID import. Local draft
files are not presented as synchronized templates. The installed app and live
card printer have not been changed or verified by this source change.

See `badge-setup-windows.md` for POS-00 installation and the Asure ID conversion
workflow, and `badge-template-format.md` for assistant-friendly authoring and
validation commands. `asure-id-conversion-prompt.txt` is the reusable handoff.

Validation: run `mvn -q -f SmartStudio/pom.xml test`. To generate a clean preview
and portable template after compiling, run:

```powershell
java -cp 'SmartStudio/target/classes;SmartStudio/target/lib/*' SmartStudio/tools/PreviewBadge.java output/badges
```
