# SmartStock 1.1.28 Windows

Build 101028 includes the shirt designer and staff setup for round-neck tees,
V-neck tees and polos, grouped quantities and quote review, existing custom-order
rounding and mixed-size sets, publishing workspace, and the latest mobile-save
transport retries and idempotency fix.

Validation: Maven package suite 1001 tests, 59 skipped, no failures/errors;
website 38 tests and gateway 39 tests pass; mobile transport, Bash security and
git diff --check pass. Exact extracted installer native startup passed with an
isolated user home. All 24 updater payloads match the installer. PostgreSQL
installer pinned hash and model-free application contents passed. Hosted model
catalogue verification passed. Packaged mobile source matches current source.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 161769406 | 2c4d3aca8bca52f1007a99da5ee673a5a0ac6067437a9aca1dbb0a4bbab93231 |
| Installer | 575138505 | 97e32bff3e950ca75dd1efec5bd2859d2d97f260ce477315af7652c6efb3b842 |

Updater hosted download matched its exact size and SHA-256 before publication;
release metadata readback matches the verified artifact. Storefront gateway
version 53722ac1-eba5-4fea-9e53-f897f86d3ccd is deployed with browse-only enabled.

The complete hosted installer download also matched its exact size and SHA-256
before selecting it publicly. The public Windows download returns HTTP 200 with
the 1.1.28 installer filename and 575138505 bytes.

The website bundle is served by the store server after installation. Update the
server before registers; configure eligible shirts before enabling them publicly.
The live website GET returns HTTP 200 but currently serves the older bundle
index-DAzS4CBR.js / index-CquQ2X-i.css, confirming installation is still needed.
Publication does not install the live store. Live database/service installation,
customer submission and quote-to-order acceptance, update/rollback, physical
shirt sample, printer/drawer/NFC and macOS verification remain outstanding.

Download: https://downloads.deckers.gy/download/windows
