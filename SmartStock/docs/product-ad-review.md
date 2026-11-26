# Product ad review

For a sequential review list, open **Inventory → Review Product Ads…** (or
**Review pending ads…** in Product Image Coverage). Pending ads for all products
and all four formats appear in one shared list. Select a preview, then click
**Approve ad and next** or **Reject ad and next**. Each successful decision removes
that ad from the pending list and selects the next one until the queue is empty.
These buttons are the explicit review action; no second per-image confirmation
is needed. A failed or stale review stays unconfirmed and requires Refresh.
Use the status selector to browse rejected or approved ads, or import/replace a
selected ad. Newly imported drafts appear when shared reviews are refreshed.

Generate the images in ChatGPT/Codex (including in another chat), then import the
finished files into SmartStock. No AI API key or API billing is needed in the app.

Open Product Image Coverage, select a product, and choose **Create / review ads…**.
Choose Square (1:1), Portrait (4:5), Story (9:16), or Landscape (16:9).
Import an image matching the selected aspect ratio, or create a simple template
from the main product photo and headline. Template composition is not AI generation.

Choose **Save shared draft**. The image is stored as pending review on the local
store server, and can be reopened on any authorized register. Closing a window
preserves saved drafts. Local edits remain unsaved until this button succeeds;
Refresh can discard local edits. Review one shared draft per product and format.
The coverage filter **Ads awaiting review** finds products with pending drafts.

Use **Approve ad** or **Reject ad** and confirm the decision after inspecting the
preview. Approval adds that exact stored revision to the gallery, updates only
its coverage slot, and preserves the main photo. Rejection keeps the draft as
rejected and preserves existing approved coverage. Replacing an approved or
rejected draft requires saving a new pending revision. Prior gallery images are
retained; the gallery limit remains 20. Refresh loads changes from other registers.
A stale revision cannot overwrite, approve, or reject a newer revision.

All operations require an authenticated HTTPS LAN session and EDIT_ITEM permission
on the server. Draft PNG bytes and review metadata live in the local PostgreSQL
product_ad_drafts table, separate from public gallery assets. Drafts are not
register reference data and are not mirrored into the cloud POS database. The
primary key supports product/format lookups. The ordered local migration is in
the schema contract and automatic server upgrade path; fresh provisioning uses
the same table and security rules. No cloud schema or register credentials change.
Approved images use the existing local asset mirror and synchronization/recovery.

Live Windows Swing, multiple physical registers, and installed-app deployment
still require verification. No deployment or publication is implied.

Rejection requires a reason of 1–2000 characters in the corrections field. The server validates and saves it with the reviewed revision. Reopen the Rejected list or the product/format review to read it from any authorized register. Saving a replacement clears the old reason and starts a fresh pending review. Historical rejections made before reasons were supported can have an empty reason.
