# SmartStudio vinyl cutting and print-and-cut setup

## Equipment verification and current status

The workstation implementation supports unprinted vinyl and Canon-printed label
jobs with manual three-target alignment on the VEVOR B-870. Physical acceptance
is still required. No cutter commands were sent during development.

The [VEVOR family instruction manual](https://device.report/m/4687b241f9c1e6431b49fb7a9439be66027054317995cd5f72b38f6a832e718b.pdf)
explicitly lists B-870 variants. Its technical table lists HPGL/DMPL and
USB/COM interfaces; the 870 family table lists a 765 mm cut width. Its panel
instructions describe offline positioning and origin confirmation. This is
family-level evidence, not proof of the connected machine's firmware, scale,
axis directions, serial parameters or maximum reliable tracking length.

VEVOR's [manual-positioning product page](https://www.vevor.com/vinyl-cutter-c_11151/34-vinyl-cutter-plotter-machine-signcut-software-lcd-display-work-with-mac-ios-p_010666425016)
currently identifies KH-870A and gives different cutting-width information.
Do not substitute that model or a camera-contour KW-870 for the user's B-870.
The setup defaults of 870 mm feed and 765 mm cut width are provisional limits.
Units/mm starts at zero, and motion is disabled until equipment verification.
The 9600 baud field is an editable starting value, not a verified specification.

An SSH inspection succeeded on 2026-10-24 after the user configured access.
The user confirmed SignMaster already cuts successfully over USB. The connected
CH554_CDC interface (VID 0483 / PID 5750) uses Microsoft's usbprint driver rather
than a virtual COM port. SignMaster V5 and Easy Cut Studio 6 are installed, as is
the Canon GP-4600S driver. No remote cutter or driver configuration was changed
and no motion commands were sent. The implementation therefore supports direct
Windows USBPRINT as well as serial. Select the matching interface from the local
device list; do not choose a printer or Bluetooth port by guesswork. To finish
verification, inspect the connected computer's Device Manager, existing cutter
software's device settings and the cutter's nameplate/manual. Record the COM
port, baud, flow control, HPGL units/mm and axis directions locally in Cut Setup.
Never include credentials or machine-specific identity evidence in this repository.

## Screen design and workflow

The Labels workspace retains its existing editor and adds **Print & Cut labels**
under Print Setup. Leaving this off preserves the ordinary label-sheet workflow.
**Vinyl Cut** is a new chooser card using the same editor, layers, undo/redo,
text/image formatting, templates and inch-based Layout dimensions.

Reuse `StudioVisuals`, Deckers components, warm-white surfaces, purple/plum
accents, bundled icons and the shared ribbon/task-window behavior. There is no
new design system or public server API. No schema or installer changes are needed.

| Screen | Main controls and behavior |
| --- | --- |
| Layout | Design width/height, units, existing artwork editing and layers |
| Print Setup | Roll/sheet setup, quantity, spacing, bleed, printer margins, grip edges, artwork/loading rotation |
| Cut Setup | Local B-870 connection and material notes, frozen job creation/opening, setup guide |
| Frozen production job | Print and loading previews, save job, print/PDF, manual alignment, pen proof and blade send |

The production window shows print dimensions and the width/length actually loaded
into the cutter. Print and cutter views share one frozen layout. Purple outlines
show cut geometry; pale purple edge bands show roller grip. Only artwork and black
registration targets print. Target numbers are screen guidance, not print artwork.

The output header opens the frozen production task when Print & Cut is enabled.
Create a new job after editing a design; an already open job intentionally retains
the exact artwork/geometry for the sheet already printed. Close it to create a
replacement. Reopened `.sscut` jobs always require fresh print confirmation,
registration and pen inspection; no saved job automatically resumes cutting.

## Media and the 0.75-inch grip allowance

Choose roll width and calculated job length, or explicit sheet/fixed length.
Calculated roll length includes every row, label spacing, bleed, printer margins,
registration bands and selected end allowances. Fixed media must fit all copies;
the application rejects overflow rather than silently dropping labels. A job
repeats the selected design page. Merge records must be cleared for this workflow.

**Roller grip allowance** defaults to **0.75 inch / 19.05 mm**. Select top, bottom,
left, right or multiple edges relative to the **printed sheet**. These bands remain
separate from printer margins, bleed, registration targets and cut contours.
They consume physical media and are never trimmed away in the output.

For a 44-inch print, use a shorter sheet dimension across the cutter and feed the
44-inch dimension through it. Enable clockwise loading rotation. The selected
physical edges rotate with the sheet: top → right, right → bottom, bottom → left,
left → top. Thus a right-side allowance on the print becomes a bottom allowance
in the loading view. Check the loading diagram before installing material.
The tested cut width may be smaller than material feed width: both paths and
registration targets must remain reachable. Oversize loaded sheets are rejected
before motion. Automatic sheet splitting is outside this version.

## Contours and artwork

Choose a rectangle, rounded rectangle, circle (equal design sides), custom closed
polygon or plain text outlines. Custom polygon vertices are editable x,y pairs in
millimetres, separated by semicolons, and must fit within the design. Raster art
requires an explicit contour; visible image edges never become implicit cut paths.
Text cutting supports visible plain, single-line text without merge placeholders,
master-page text, stories, WordArt or fit-to-box effects. Unsupported text effects
are rejected; use an explicit contour instead. Vinyl preview displays actual cut
geometry rather than claiming unsupported decorative text will be cut.

Bleed expands artwork outside the trim rectangle while preserving actual-size
artwork inside it. Review the result for designs with fine details at the edge.
An optional outer weeding border requires at least 2 mm bleed clearance. No
force, speed or blade-offset command is guessed: set these on the machine and
record the tested values with the local named material settings.

## Canon GP-4600S printing

Consult [Canon's paper-size documentation](https://ij.manual.canon/ij/webmanual/Manual/All/GP-6600S/EN/LBGA/lbga_b302.html)
for supported media and driver limits. Select the actual loaded media type and
roll width, then define a custom page matching the full production dimensions.
Disable scaling/fit-to-page and borderless expansion; use **Actual size / 100%**.
Printer media cutting must retain the selected grip bands. Use inkjet-compatible
label media appropriate for the GP-4600S and allow the print to dry before cutting.

The Java print path requests physical page dimensions, rejects driver page-size
changes and checks the imageable area against the production area. PDF export
uses the identical frozen painter and physical page size. The operator must still
check scaling in an external PDF viewer/Canon driver. Measure a known label width,
height and distance between registration targets before confirming a physical print.
The software cannot prove driver scaling or that printing physically completed.

## Direct cutter setup and calibration

1. Confirm B-870 on the nameplate. Install the vendor USB/serial driver; find its
   USBPRINT interface or virtual COM port. Close SignMaster and other cutting
   software holding that interface. Direct USB does not use baud or serial flow control.
2. Copy the actual serial baud, 8N1 and flow-control setting into B-870 setup.
   Enter the verified HPGL scale and axis mapping. Until these are known, leave
   equipment verification off. Reconfirm it after restarting SmartStudio.
3. Install the pen. On the machine panel establish one common origin with room
   for a calibration square. The adapter never sends `IN`, a homing command or
   a speculative origin-reset command. Logical cutter coordinates increase
   rightwards and down the loading diagram; axis mapping converts these to HPGL.
4. Connection test only opens/closes the port; it does not prove device response.
   Use the bounded absolute pen-up jog and 10 mm pen square to verify scale and
   directions. If a commanded square measures M mm instead of 10 mm, correct
   units/mm by multiplying the current value by 10/M; check both axes again.
   **Cut Setup → Measured pen calibration** calculates this correction from both
   measured sides of the 10 mm square. It rejects measurements differing by more
   than 2% between axes, since a shared scale cannot correct tracking distortion.
   Applying a correction invalidates previous proof settings; repeat the pen
   square and job proof before blade cutting. This helper sends no motion itself.
5. Establish reliable feed/cut limits and tracking length from physical tests.
   Select blade exposure and low initial force on scrap vinyl; increase only
   as needed. For labels, kiss cut the face/adhesive without cutting the backing.
   Record material, speed, force, blade offset and observations in setup notes.
6. Print and load a frozen label job. Jog to its three printed crosses and enter
   their measured logical cutter coordinates in mm, always relative to the same
   origin. Measurement fields start blank so expected positions cannot be mistaken
   for captured physical measurements. Enter all six measured coordinate values.
   Do not reset the panel origin between targets. An affine transform
   handles placement, rotation, scale and skew; mirrored/degenerate results fail.
7. Send the pen proof, wait for actual motion to finish and inspect first/last
   rows and all contours. Confirm the proof only after checking it physically.
8. Replace the pen with the tested blade without shifting material/origin and
   send once. HPGL write completion means commands were sent, not that the
   machine finished. Monitor the cutter at its panel.

Windows USB transport uses the existing JNA dependency to open the verified
USBPRINT interface exclusively and write raw HPGL, without a printer queue or
driver installation. Its device-path validation rejects filesystem targets.
The synchronous Windows USB write has no application-level timeout; if the OS
driver stalls, resolve the device at Windows/the cutter before closing Studio.
USB enumeration may include previously attached devices; opening a disconnected
interface fails without sending commands. Native USB remains physically untested.

Serial transport uses jSerialComm 2.11.4 (Apache-2.0/LGPL-3.0 dual licensed;
[project and notices](https://github.com/Fazecast/jSerialComm/tree/v2.11.4)).
It sends absolute HPGL with pen-up travel and flattened vector contours. Native
serial runtime is bundled as a Maven dependency; it adds no models or cloud API.
On failure there is no automatic retry/resume. Stop the machine at its panel,
inspect any partial/buffered cut, reload or replace material and re-establish
origin and alignment. Signing out/closing is blocked while sending is active.

## Persistence and validation

`.sscut` saves a versioned job containing artwork, embedded assets, media settings
and contour selection. Device connections and proof confirmations are not embedded.
Local media/material/cutter settings live under the active Studio user's
`cut-production` directory and are not committed or synchronized as credentials.
Settings use millimetres internally and inches in the setup UI.

Implementation stages are media/layout → actual-size outputs and persistence →
HPGL adapter and bounded pen calibration → three-target registration → physical
acceptance. Automated tests cover geometry, edge rotation, invalid inputs,
frozen artwork, serialization, registration, command bounds, transport failure,
PDF dimensions and actual-size printer rejection. Swing previews and chooser/ribbon
layout are rendered at the project's display budgets for inspection.

Physical acceptance remains outstanding: Canon driver and printed measurements;
B-870 port, firmware units and axes; pen proof; vinyl test; label kiss cut;
rotated 44-inch sheet; end-of-job registration/tracking; and confirmation that
the selected 0.75-inch grip prevents slipping. Record measured error at the start
and end of the longest intended job and set the usable tolerance from that test.
Installed Windows application/native serial packaging and macOS hardware checks
are separate verification steps. No release or live installation is authorized.
