# Editing and cancelling outgoing store transfers

The Store Transfer screen uses the Transfer Desk layout. Its left queue has
**Incoming** and **Outgoing** selectors, transfer search, and a **New transfer**
button. The three-dot button refreshes the queue and store destinations.

Select a pending outgoing transfer to edit its products, quantities and note
directly in the right-hand workspace. **Save changes** requests the edit;
**Cancel transfer** requests cancellation. **Discard edits** reloads the saved
transfer. The destination stays fixed; cancel and create a new transfer to change
stores. Unsaved changes are protected when changing transfers or closing.

For a new transfer, choose a destination, search products by name or SKU, select
a result, enter a quantity, and choose **Add item**. Down/Enter also select and add
a search result. Line quantities can be edited, and each line has a remove
action. **Save draft** saves the unsent destination, items, quantities and note
on this computer, separately for the current environment, source store and
signed-in user. Drafts survive closing the window and restarting SmartStock.
They do not reserve, deduct or synchronize stock and are not visible on other
computers. A destination or items can be left unfinished when saving.

Choose a draft under **Saved drafts · this computer** and press **Open** to
resume it. **Save draft** updates the opened copy. **Delete** confirms removal of
the saved copy; current items stay in the editor as unsaved work. **Clear draft**
clears the editor and leaves previously saved copies available. **Send transfer**
uses the authenticated server's normal validation and stock policy. A successful
send removes the saved copy; a failed send retains it. If local cleanup fails
after sending, the user is warned to delete the leftover copy before reusing it.
Reopened drafts do not show stale stock counts; availability is checked when sent.

Inventory results appear inline below the search field. They do not use a Swing
popup or capture input across application windows. Escape dismisses the results;
changing the search text clears the previous selection immediately.

Keyboard-wedge scanners ending in Enter can submit a lookup before its network
response arrives. The desk retains that submission until the matching current
query returns, adds a single unambiguous result once, and clears the search for
the next scan. Multiple matches require explicit selection. Superseded queries
and duplicate in-flight responses cannot add stale or duplicate lines.
Escape and every text edit also invalidate in-flight responses immediately,
before the next debounced search starts. Results use a reusable plain-text
renderer and fixed row measurements, avoiding HTML layout for large result sets.

Select an incoming transfer to review its products and quantities. **Receive
transfer** asks for confirmation using the loaded item list. The server checks
that these are still the reviewed quantities before adding stock; a concurrent
edit requires refreshing and verifying again. Saving edits likewise checks the
original items and note to prevent overwriting another cashier's changes.

Both stores must run a version supporting transfer changes and synchronize.
Changes use the existing durable transfer envelope and receipt acknowledgement
routes. The outgoing row shows **Awaiting store confirmation** until the
destination acknowledges the request. No manual receiving-store approval is
needed. A received transfer cannot be edited or cancelled. If receiving wins the
race, the change is rejected and no outgoing stock is restored.

Cancellation restores source stock only after the destination has marked its
copy cancelled. An accepted edit adjusts source stock by the difference between
the original and replacement items, records inventory movements, and preserves
the transfer's identity. Quantities follow the existing transfer policy, which
allows negative inventory. Duplicate requests and acknowledgements do not apply
stock adjustments twice. A receipt delivered before an edit acknowledgement is
retried after the stock adjustment has applied.

The authenticated LAN server enforces STORE_TRANSFER permission, source-store
ownership, valid products/quantities, pending status, and request idempotency.
Synchronization validates change event ownership and serializes changes against
receiving using the transfer row lock. Request and acknowledgement state use
existing transfer status and outbox/inbox storage; no schema migration or new
cloud routing is required.

Before deployment, verify the Swing workflow and end-to-end synchronization on
two installed stores, including an offline destination and receiving while a
cancellation is in flight. Local integration tests use disposable PostgreSQL
schemas and do not verify hosted synchronization or installed applications.
