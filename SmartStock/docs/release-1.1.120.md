# SmartStock Windows 1.1.120 — 2026-11-15

Sequential shared ad review is available under Inventory > Review Product Ads.
Approve ad and next and Reject ad and next advance through the shared pending
list across products and all four formats. Rejections require a reason of
1–2000 characters, validated on the server and visible on authorized registers.
Saving a replacement clears the old reason and starts a pending revision.
Revision checks prevent stale decisions; failed reviews do not skip images.

Full Maven suite including disposable PostgreSQL checks passed before packaging,
including queue progression, failed/stale review, required/bounded reasons,
persistence and replacement clearing. Security and whitespace checks passed.
Packaging skipped repeating the completed test gate. Exact ZIP applied over an
isolated 1.1.119 app: installed JAR identity, new review classes/migration,
launcher configuration, runtime/profile preservation, model exclusion and native
startup passed. Hosted model catalogue readiness passed; no models changed.

Artifact: D:/SmartStock-release-builds/1.1.120/smartstock-windows-1.1.120.zip
Size: 169228947 bytes
SHA-256: 62805393f5edf12409a22c92c728d7c1acf06806e18a5d58f499b333df482860
Build: 101120

Update the server before registers. No live app/service was updated or restarted.
Live multi-register/database/service, physical printer/drawer/NFC and macOS checks
remain separate. SmartStudio and first-install selections are unchanged.

Published after full hosted download/size/SHA-256 verification. Independent read-back matched version, build, immutable artifact path, bucket, size, hash and published=true.
