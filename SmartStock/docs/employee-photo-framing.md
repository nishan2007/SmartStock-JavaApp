# Employee photo framing

In Employee Management, **Choose** opens a photo editor. Drag the image and use
Zoom to compose it. The circle shows the web avatar crop; the overlapping portrait
rectangle shows the standard badge photo. Both have live previews. **Fit original**
resets the composition with headroom. Cancel leaves the employee's selection alone.
Click **Update Employee** (or add/approve the employee) to persist the change.

**Reframe** opens the saved original and previous position/zoom. Original bytes
are stored as a separate authenticated employee image asset; the displayed photo
is a rendered copy. Other workstations receive both references and framing through
the existing employee reference synchronization. Clear removes all three employee
references when the employee is saved. Existing photos can be reframed without
re-uploading them manually; until then the web avatar fits the whole photo inside
the circle instead of center-cropping the head.

The rectangle uses the default badge photo ratio (165:190). Custom badge templates
with a different photo ratio can crop differently; check their badge preview.
Employees cannot edit their photo through the web account screen. Neither the
original reference nor framing metadata is included in that screen's profile API.

The local/cloud baseline and ordered migration add `employee_photo_source_url`
and `employee_photo_frame` to `users`. Existing local stores use the guarded,
transactional schema-contract upgrade. No additional index is needed: writes and
reads use the existing users primary key. Publication must include the cloud
migration using the normal release procedure.
