# SmartStock 1.1.70 Windows update

Build 101070, published 21 October 2026. Update only; no first-install package
was created or selected, and no live application was installed or restarted.

The cumulative application includes the outstanding shared-workspace changes:

- Shirt sets with quantities per size, including VARIABLE catalogue pricing.
- Explicit size, colour and shirt-number columns; expanded cart shows every
  shirt and the summary groups shirts by size.
- Optional print colour and individual names/text on each add-on, with fields
  labelled by size and shirt number. Shared descriptions remain the default.
  Editing and Hold/Resume retain the shared template and each shirt's text.
- Business Cards, Large Format Printing and Laser product flags, inventory
  filters and website sections, including ordered local schema migration.
- Custom-item variant SKU, barcode and alternate-barcode search fixes and
  variant photo grids for custom items without a main photograph.
- Existing cumulative overseas-order, saved-photo-record, shared-template,
  account-charge receipt/signature, shirt-designer and storefront changes.

SmartStudio source matches the already-published 0.1.62 release. Its installer
and the existing SmartStock first-install selection were retained.

## Verified artifact

File: smartstock-windows-1.1.70.zip

Size: 167328269 bytes

SHA-256: 75d2bcde1dcb7f51635e518dff33a8d607dee9a9e6757dfd8a34fb85d5437b7c

Object: windows/1.1.70/75d2bcde1dcb7f51635e518dff33a8d607dee9a9e6757dfd8a34fb85d5437b7c/smartstock-windows-1.1.70.zip

The publisher downloaded the complete hosted object and verified size and
SHA-256 before creating the application release row. An independent protected
read confirmed version, build, platform, bucket, object path, size, SHA-256 and
published state. Evidence remains under target/release-windows-1.1.70.

## Validation and rollout

Maven package/tests: 1193 tests, zero failures/errors, 96 skipped. Website:
38 tests plus type check and fresh production build. Storefront gateway:
62 tests. Security, whitespace, model-free ZIP and hosted immutable model
catalogue checks passed. All 1072 recorded source inputs remained unchanged.

The exact ZIP was extracted into an isolated copy of the existing native app
and runtime. Its application JAR matched the packaged JAR; expected cumulative
feature classes and the new migration were present. The native app started
successfully with a separate user profile and reached its welcome window.
No model weights were packaged or published.

The storefront gateway update retained the existing registered production
origin and protected secret binding. Version
1a9d4509-f76a-41f5-8251-d5f5f477eb2f was deployed; the main site and shop returned
HTTP 200. The updated server-side website bundle arrives with the app update.

Update the store server before registers. Live installed upgrade/rollback,
database migration, services, authenticated order entry/hold/resume, physical
scanner, printer, drawer and NFC verification remain separate checks. No macOS
release was built or published.
