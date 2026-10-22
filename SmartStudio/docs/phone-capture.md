# Phone capture over Wi-Fi or the internet

For several people, or when no template should stay open, use the shared
[Photo Queue](photo-queue.md) workspace instead. The direct capture flow below
continues to send one photo to the currently open passport workspace.

In **Passport Pictures**, choose the Guyana or US template and click **Take photo
on phone…**. Scan the private QR link with the phone's camera, take or choose a
photo, and tap **Use photo**. SmartStudio checks for the incoming photo every
1.8 seconds, fills the sheet, and opens the crop/zoom editor. The phone may use
mobile data or another Wi-Fi network; the computer must remain connected to its
paired store server, and both computer/server and phone need internet access.

Keep the workspace and QR window open while sending. Closing the QR window,
switching the passport template, leaving the workspace, signing out, or choosing
a local photo cancels the pending request. Late responses from cancelled requests
cannot replace the current photo. A new link is needed for each retake.

## Deployment

This feature requires all three updated components:

1. The storefront Worker, including its new `CAPTURES` binding and
   `v2-studio-capture` SQLite Durable Object migration in both Wrangler configs.
   Use the repository's protected production storefront configuration and
   preflight/deployment process; do not deploy the empty checked-in origins list.
2. The store server with `/v1/studio/phone-capture`, plus its existing protected
   website connection settings. The existing origin key and instance identity
   authenticate outbound relay requests. No new credential is needed.
3. SmartStudio built against the updated SmartStock client library.

Deploying the website alone does not activate an older installed server or
SmartStudio. Publishing application packages does not install them into a live
store. On 2026-10-17 the website was deployed, SmartStudio 0.1.40 was published
and installed here, and SmartStock 1.1.42 was published for the operator to
install. Live delivery remains pending that installed server update.

## Temporary data and access

The phone link carries a random upload-only capability in its URL fragment,
which the page removes from browser history when loaded. The capability is never
sent as a query parameter; the relay keeps only its SHA-256 hash. The phone cannot
retrieve photos or access store records. The desktop poll is authorized by the
store server and bound to the staff login session, device, and location.

Photos are temporarily held in an isolated cloud relay, outside the store catalog,
for at most the 15-minute session lifetime under normal relay operation. Cloudflare
alarms remove abandoned photos; polling also enforces expiry. SmartStudio requests
deletion after receipt or cancellation. If that request cannot reach the relay,
expiry cleanup remains scheduled. Phone capture does not save the image in a
template library, public website, product record, or recovery backup.

The phone converts the photo to JPEG with a maximum edge of 1600 pixels and a
2 MiB upload limit. This preserves enough pixels for the passport print sizes
without transmitting full camera originals. SmartStudio validates image dimensions
before decoding. Framing and passport-photo compliance still require staff review.

## Verification

All 107 SmartStudio tests pass; the SmartStock Maven test suite, repository
security check, and whitespace check pass. All 43 storefront Worker tests pass,
including capture ownership, cross-store/session denial, upload authorization,
one-time use, body limits, expiry, cancellation, and cleanup. Wrangler dry-run
bundling passes with both Durable Object bindings. A real JPEG was sent through
create/upload/poll/ack in the local Cloudflare runtime and matched byte for byte;
another session was denied and the photo was unavailable after acknowledgement.

Live phone camera/browser testing, internet-to-installed-desktop delivery, store
server rollout, and physical printing remain separate acceptance checks.
