# Missing template size review

Open **Custom Order Items > Custom Inventory > Review missing template sizes**.

The queue starts with sublimation inventory. Clear **Sublimation only** to include other non-shirt inventory. Inactive/archived items and completed templates are excluded. Items that track variants are reviewed per active variant, not as an extra parent entry.

Select an item, inspect its photo and identity, enter the actual printable width and height, and explicitly choose IN, CM or MM. Existing partial dimensions are prefilled. **Save & Next** writes only these three template fields through the authenticated custom catalogue mutation route and advances after success. **Skip for now** leaves the item incomplete; it returns when the review is reopened. Completed entries remain out of future queues. Close and refresh if another computer changed the template.

A parent photo is used when the variant lacks its own photo. Missing photos do not block measurements. No template dimensions are inferred from product size/capacity, and saving does not create a 3D model or enable a new designer.

The server verifies item/variant ownership, active inventory eligibility and expected prior template values. Writes use the existing catalogue permission, transaction and idempotency enforcement, and preserve price, stock, classification, photos and other settings. No schema change is needed.

Validation: 4 queue/request/validation/write-contract tests, 1 disposable PostgreSQL integration test and 12 existing Custom Inventory tests passed without skips. Security and whitespace checks passed. Live installed Windows UI and server/register acceptance remain outstanding; source changes are not in the previously published immutable release.
