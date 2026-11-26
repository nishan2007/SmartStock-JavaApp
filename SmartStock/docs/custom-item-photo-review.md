# Custom item photo reviews

The existing Product Photo Review screen and shared review table also support
custom items and variants. Regular product IDs stay positive. Custom items use
`-custom_item_id`; variants use `-1000000000-custom_variant_id`, matching the
existing POS catalog convention. These IDs are review identities, not rows in
the regular products table. No schema migration is required.

The server resolves the owner before loading or promoting a photo. Custom
items must be active and unarchived; variants must also have an active,
unarchived parent. Custom imports require the exact approved shared JPEG.
Promotion retains the original in the same owner's additional photo gallery,
checks the source reference and SHA-256, and uses the existing permission,
transaction, revision and idempotency protections. Uploaded assets retain the
correct CUSTOM_ITEM or CUSTOM_VARIANT category.

The server must include this support before uploading signed-ID reviews.
Previously installed servers reject these IDs. Do not insert custom reviews
under positive IDs or replace regular product reviews to work around that.
Keep operational manifests, originals and generated photos outside source
control. Preflight current source references and digests before upload, retain
existing reviews on conflicts, and leave new previews awaiting human approval.

For the current custom-photo request, shirts and plaques are excluded. Missing
source photos must be identified to staff rather than guessed from item names.
Where only variants have source images, review the variants independently.

Validation: focused owner-routing and signed-ID parsing tests, gallery
promotion tests, full Maven suite, security check and whitespace check. Live
installed-server approval and storefront verification remain separate checks.
