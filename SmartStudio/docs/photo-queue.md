# Shared photo queue

Open **Photo Queue** from SmartStudio's workspace chooser, then click **Phone
upload link / QR…**. Scan the private link once and take photos for several
people, adding an optional name/reference for each. After each **Add to queue**,
the phone page is ready for the next person. It works over mobile data or Wi-Fi;
no passport template or desktop photo editor needs to remain open.

The upload link lasts 12 hours. A template or SmartStudio may be closed after
issuing the link; the store server and website connection must remain online.
Uploads are acknowledged only after the local server has saved them. If the
server cannot be reached, the phone keeps the selected photo for retry. Retrying
an acknowledged-but-timed-out upload does not create a duplicate queue entry.

On any signed-in, Studio-enabled computer paired with that store, open **Photo
Queue**. The list refreshes every four seconds. Select a person to preview their
photo, then click **Use in Passport Pictures** to load it into the current Guyana
or US template and adjust crop/zoom. Selecting a photo does not remove it from
the queue. Staff should coordinate who is working on a photo when several
computers use the queue simultaneously; this version does not reserve entries.

After a print job is sent, SmartStudio asks whether the sheet physically printed
successfully. Confirm **Mark printed** only after checking the sheet. The same
action is available in the passport toolbar if confirmation was deferred. A
cancelled or failed job never automatically starts deletion. Printer spooling
alone does not prove that paper printed successfully.

The server schedules deletion exactly 24 hours after the first confirmation.
Reprinting or retrying confirmation does not extend the deadline. Printed photos
are hidden by default; **Include printed photos** makes them available for a
reprint during those 24 hours. Expired photos are immediately unavailable to
queue reads and print validation; the server cleanup task removes their files
within its next one-minute pass while running. If stopped at the deadline, it
cleans up on restart. Unprinted photos stay in the queue until printed or manually
deleted. **Delete selected…** removes a photo immediately after confirmation.

The queue holds at most 300 photos per store, with a 2 MiB limit per upload.
The phone produces a JPEG with a maximum edge of 1600 pixels. Originals, names,
and print/deletion timestamps persist under the active server profile's
`studio-photo-queue/store-<location>/` directory, outside application/rollback
folders. This is local private data, not template-library artwork or storefront
catalog content. It is not uploaded to template recovery backups or synchronized
to Supabase. Small anonymous deletion markers prevent upload retries from
recreating removed entries; they expire after 24 hours and contain no photo/name.

Access to listing, viewing, printing status, and deletion requires authenticated
LAN device and staff sessions with Studio access, scoped to their current store.
The phone capability permits uploads only. The public gateway sends each photo
only to its selected store origin; it never uses another store as a fallback.

## Deployment and verification

Requires updated storefront Worker, store server, and SmartStudio. The queue
uses the existing protected website origin connection and the existing staff
session/device authorization. It needs no new database schema, cloud database,
or public image bucket. Existing direct-to-template phone capture remains available.

Regression tests cover multiple uploads, persistence across server restart,
store isolation, bad/expired capabilities, size/image validation, idempotent
retries, interrupted-upload cleanup, deletion, and an immutable 24-hour deadline.
Gateway tests cover selected-origin routing, lack of failover, limits, cross-origin
rejection, and public access isolation. Full physical printer output, real phone
capture, and cross-computer acceptance require deployed components and remain
separate checks. On 2026-10-17 the website was deployed, SmartStudio 0.1.40 was
published and installed here, and SmartStock 1.1.42 was published. The operator
reserved the installed SmartStock update for themselves. Live queue acceptance
is pending that server update; see the matching release records.
