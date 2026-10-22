# Editing badge templates

Badge Cards, Business Cards and Labels have a visible **New template** action
above the editor toolbar. Their template choosers also have a direct **Create
new … template** button that remains available while searching the library.
Existing unsaved-change prompts still apply. Add artwork and fields, then Save
to keep the new design in the template library; shared saves require signing in
and connecting to the store server. For badges, set the template name under
Template before saving.

Insert image… opens a PNG/JPEG file and adds it as a new artwork layer. Paste
image (or Ctrl+V while the canvas has focus) adds a copied image or image file
as a new layer. Existing layers and personal photo fields remain unchanged.
New images retain their proportions, start centered on the page, and are selected
for moving/resizing. These actions are available in Badge Cards under Insert
and in Business Cards under Template & insert. Artwork is embedded in saved
templates; badge insertion participates in undo/redo. Text fields retain their
normal text-paste behavior.

Each photo field holds its own personal image for the current card. Select its
box on the canvas or its row in Layers, then use Replace selected image / photo
or Choose / replace selected photo in Details. With one photo field, Details
selects it automatically. With multiple fields, select the destination first.
Clear selected photo empties only that field. Clear details, template replacement,
and sign-out clear personal photos. Personal images are not saved in templates.

Artwork means a permanent template graphic, such as a logo or background. The
same replacement action updates artwork in the saved design, while replacing a
PHOTO field only changes that field's current personal image.

Choosing/replacing a personal photo opens Frame photo before applying it. The
preview has the exact aspect ratio of the existing photo field; the field's
physical dimensions do not change. New photos start at Fit whole photo so the
entire picture remains visible. Drag to position, adjust Zoom, choose Fill box,
rotate 90 degrees, or Reset. Background defaults to off, leaving uncovered areas
transparent. Enable Background for white fill, or choose a different color.
Outline is independently optional and has its own color picker; both default
off. The background fills behind the image and does not remove existing photo
background pixels. These appearance settings are saved per field in the template,
unlike personal photos and framing. Apply photo commits the
framing; Cancel keeps the previous photo and framing. Crop / frame selected
photo reopens the editor without selecting a new file. Preview, PNG, PDF and
printing use the same framing renderer. The original image remains intact.
Framing is session-only personal data, cleared alongside photos and excluded
from saved templates. Template undo preserves the current per-field framing;
photo framing itself is edited/reverted in its dialog rather than template undo.

Select a layer and press Delete on the canvas or in Layers to remove it.
Delete in a text editor continues to remove characters normally. Badge layer
deletions and resize gestures participate in the existing undo/redo history.

Selected objects have eight resize handles. Drag a side handle to stretch one
dimension; drag a corner to preserve the object's width/height ratio while the
opposite corner stays fixed. Resizing changes the object box, not the font size.

Double-click text on the canvas to type directly into its box. Click outside or
press Ctrl+Enter to apply, or Escape to cancel; Enter inserts a line break.
For badge fields linked to personal details, inline editing changes the current
detail value while preserving its template binding. Literal badge text and
business-card template text (including merge placeholders) edit the design.
These canvas interactions are also available in SmartStudio Business Cards.

Click a text box, photo or shape in the canvas and drag to move it. The object
stays selected until the mouse is released. After clicking an object, arrow keys
move it by 0.1 mm; hold Shift for 1 mm steps. Arrow keys in text-entry fields and
the layer table retain their normal editing/navigation behavior.

Choose Rectangle, Ellipse, Triangle or Line next to **Add shape**, then add it.
The new shape is selected. Change X/Y, width/height and colour in the layer table.
Colour uses `#RRGGBB`. A line is a horizontal bar; adjust its height for thickness.
Use **Bring forward** and **Send backward** to arrange shapes behind or in front
of text and photos. The topmost object under the mouse is selected; use the layer
table to select covered objects.

Shapes are reusable template objects and appear in card previews, PNG/PDF output
and printing. Objects outside the white print area remain editable but are
clipped from card output. Save changed templates before switching designs.

Publishing Workspace uses the same arrow-key steps after clicking an object on
its canvas. Locked objects remain fixed; movement supports undo/redo.

## Copy and paste layers

Select one or more layers on the canvas or in Layers, then use **Copy layers**
or **Ctrl+C** (**Cmd+C** on macOS). Open the destination page or another template
in the same SmartStudio session and use **Paste** or **Ctrl+V** / **Cmd+V**.
Pasted layers retain their positions, formatting, images and relative order;
they are selected after insertion and can be moved together. Repeated paste
creates independent layers. Badge layers paste into badge templates; page layers
paste into page templates. Personal badge photos are not included in copied layers.
A paste that exceeds the destination template's limits is rejected without
changing the template.

Copying plain text from another application and using Paste creates a text layer.
Copied images and supported PNG/JPEG files create image layers. While editing
text or a table cell, the editor keeps its normal text clipboard behavior.
Changes can be undone using the existing template undo controls.

Local photo selection/replacement, file drops and pasted PNG or JPG/JPEG images
accept up to 20 MiB per image, with the existing 12-megapixel limit. JPG and
JPEG filename extensions are both supported. Background removal/enhancement
and phone/web submission services retain their separate processing/upload limits.

Photo-field replacement additionally accepts sources up to 100 megapixels within
the 20 MiB file limit, decoding a smaller version (at most 12 megapixels) for
cropping and record storage. This avoids rejecting large camera JPGs.

Double-click a photo field on the badge canvas to choose or replace its photo.
The clicked field is selected first; the normal crop and person-details flow
follows. Double-clicking text still opens text editing.

Use Edit photo / crop / background… beside Choose / replace to reopen the
selected photo after adding it. Crop, zoom, position, rotation and color
corrections remain editable. The framing dialog includes Remove background,
using the authenticated store server and its installed Best removal model.
A bounded processing copy handles large camera sources; framing and style
are retained when the cutout returns. Restore photo background restores the
photo as it was when that editing dialog opened. Cancel leaves the card unchanged.

Select an image or badge photo and set Transparency % in the picture toolbar.
0% is solid and 100% invisible. The setting is saved with the design and used
by canvas, export and print rendering; existing designs default to 0%.
