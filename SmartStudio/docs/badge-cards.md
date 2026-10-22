# Badge Cards: Skeldon Primary reconstruction

For the server-managed shared library, local cache, offline restrictions and
OneDrive backups, see [Shared badge-template library](shared-badge-library.md).
The local-folder workflow below describes earlier releases and development
panels without a connected shared library.

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
and photos clear at sign-out and do not enter template files. Optional print
records are described below.
PNG and single-page PDF exports use 300 DPI; PDF page dimensions match the card.
Those production outputs default to local `Documents/SmartStudio/Badge Output`,
not the shared template folder.
Templates are limited to 8 MB, 64 layers, and 12-megapixel artwork.

Users may select the same shared OneDrive folder on each computer; Windows
handles file synchronization. An externally changed local template is detected
before ordinary saving so its edits are not silently overwritten. This is not
server-managed synchronization and cannot detect undelivered remote changes.

This first section does not yet implement server-managed cross-store OneDrive synchronization,
server-managed template permissions/revisions, batch
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

## Optional print records

Each badge template has **Save print records (off by default)** in its details.
Save the template to the shared server library after enabling this option.
Existing templates keep the option off. **Enter card details** edits each text
layer's Data field binding (for example `fullName`, `idNumber`, or custom fields).
Choosing a photo also opens these details. Bind additional text layers in Layers
to add more entry fields. Blank fields keep the template's placeholder behavior.

When enabled, accepting the print dialog saves the entered values, original
photos and crop/rotation settings to private PostgreSQL history on the store
server before submitting the print job. A save failure prevents submission.
Canceling the print dialog saves nothing. A printer failure after saving can
leave a record of an attempted job; history is not proof of physical printing.
PNG/PDF exports remain explicit files and do not create print records.

**Print records (admin)** lists only the selected template's records, newest
first, in pages of 100. The authenticated server checks the active ADMIN role
for both listing and opening records and checks template/store access. Photos
and personal details are never placed in the shared template cache or reference
sync. The local schema contract installs the table/index for both new and
existing server provisioning; no register database credentials are introduced.

Opening a record fills the CURRENT template's matching fields and photo slots;
it never restores a past design. Keep Data field bindings and photo slot IDs
stable when updating a design. Removed fields are ignored; new fields start
blank. Review the current preview and use Print to reprint. Each submitted job
creates another record while saving is enabled. Disabling saving stops new
records; existing records remain accessible to administrators. With saving off,
card entry/photos remain session-only and clear at sign-out or program exit.

Source verification does not install this migration into a live store or verify
the Windows installed application, LAN session behavior, or physical card printer.

### Adding entry fields to a new design

Use **Add text field…** in the layer tools, enter a friendly label such as
**Full name**, and choose `fullName` (or `idNumber`, `grade`, `phone`,
`department`, or a custom binding). The text layer and blank entry box are
created together. **Bind selected text…** converts an existing text layer to an
entry field. Binding names start with a letter and contain up to 40 letters,
numbers or underscores. Multiple text layers can share one binding/input.

New bound text layers contain no saved personal value or placeholder name.
Type directly into the details inputs or use **Enter card details…**. Choosing
a photo clears the previous person's text entries and opens the same labeled
entry form. **New person / clear details** clears all entries and photos.
Save the template to retain the bindings; entered personal values remain
separate from the reusable design, with persistence controlled by the optional
print-record setting above.
