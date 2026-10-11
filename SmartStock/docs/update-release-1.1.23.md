# SmartStock 1.1.23 Windows

Build 101023 adds store-wide Hold Cart and Resume Held Cart for custom orders.
Customer details, individual quantities and grouping, design dimensions and
orientation, add-ons, discounts, notes, due date, draft payment choices and
attachments are preserved. Holds do not create orders, reserve stock or record
payments. Attachment chunks are verified by size and SHA-256 before availability
and before resume. A transaction claims the hold once; retry uses the original
resume key. Temporary manager approvals are removed from held drafts and obtained
again when required at checkout. Incomplete holds can be cancelled safely.

Custom Inventory now places Description immediately after Name and offers an
optional Template Size column alongside the optional Date Added column.

The local baseline, runtime upgrade, migration, indexes and recovery/mirror
ownership rules include held carts and their attachment chunks. Historical order
warming remains on demand. Generated website text assets now use LF on checkout
so the recorded bundle hashes survive a fresh Windows clone.

## Validation

- Packaged SmartStock Maven suite: 952 tests, no failures/errors, 56 skipped.
- Isolated custom-order commit snapshot: 933 tests, no failures/errors, 48 skipped;
  this run included held-cart and pending-pricing integration checks against the
  disposable loopback PostgreSQL cluster. No production database was changed.
- SmartStudio suite: 28 tests passed using the exact SmartStock 1.1.23 client JAR.
- Publisher/model/update checks: 8 tests passed. Installer portal: 8 tests passed.
- Repository security and whitespace checks passed.
- Update ZIP layout/version/model-free and hosted model catalogue checks passed.
- Extracted installer contains no model weights. Its app JAR matches the updater
  ZIP byte for byte. The exact extracted launcher started with bundled Java
  17.0.20 and a separate user profile; diagnostics confirmed WelcomeFrame visible.

Live two-register held-cart acceptance, installed Windows service/setup wizard,
printer/drawer/NFC, model download/offline inference, update/rollback and macOS
validation remain outstanding. No live app, store database or Windows service
was installed, updated or restarted. SmartStudio source changes are included in
the source checkpoint; its separate installer/channel is not published by this
SmartStock release.

## Exact artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 149087105 | 018a35caed853092a9cb6aa6bd0cb7bc558bebe5ba03bb5a299147c3295d0832 |
| All-in-one installer | 562458615 | cbc2575643f1fbd92a47add5f26033f46de76be44c629ea7917bf6cdde119a49 |
| Model migration bridge | 3318 | 09d4fd92d6c6a705395d68e4b32a6e0b567c61c77f608e7e7298cfcd9e832853 |

## Publication

The updater is published. Complete hosted download matched its exact size and
SHA-256 before metadata publication. Metadata readback matched version, build,
platform, object path, size, hash and published status.

The all-in-one installer is published. Its complete hosted download matched the exact size and SHA-256 before the public installer selection changed. Public HEAD returned HTTP 200, the 1.1.23 filename and 562458615 bytes.

Download: https://downloads.deckers.gy/download/windows

Update the store server first, then registers. Publishing does not install the
release into running stores. macOS publication remains outstanding.
