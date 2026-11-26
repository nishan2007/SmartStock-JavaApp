# SmartStock 1.1.144 Windows quick update

Build 101144; frozen source and release evidence are retained at
`D:/SmartStock-release-builds/1.1.144`.

This cumulative SmartStock update includes the current shared workspace and the
other chat's Store Keys checkout change. SmartStudio source is unchanged from
published 0.1.108 and is not rebuilt.

The existing Rosehall production server's first updated schema-readiness pass
backs up custom stock balances transactionally, records stock-count movements,
zeros its custom-item and variant quantities, and preserves a completion marker
through off-site backup/recovery. Exact store/migration guards exclude Skeldon
and new installations. Later restarts and updates preserve newly received stock.
The earlier manual reset is recognized. Skeldon quantities are preserved; the
audited Rosehall order deducted its own stock, so no Skeldon add-back was justified.

Store Keys checkout now permits a key without an NFC tag. Server-side permissions,
store assignment, active employee, version and custody enforcement remain in place.

Validation: 1654 Java tests, zero failures/errors after creating the missing
disposable Store Keys schema database and rerunning that check; 208 optional
skips. Rosehall correction and eight Store Keys tests used isolated loopback
PostgreSQL clusters. Website 83, provisioning 11, model publisher 5 and archive
validator 3 tests passed. Security and whitespace passed. Exact packaged SQL,
baseline schema, changed classes and JAR identity were checked. Model-free ZIP,
hosted model catalogue, isolated updater/backup/config/model preservation and
native launcher startup passed.

ZIP: 170634886 bytes. SHA-256:
`a3226226fe00446c23d5003174ec1b60847dfd60d0897d6839b3605cc42db810`.
The protected publisher verifies the complete hosted download before metadata
creation; publication and independent readback receipts are beside the artifact.

Windows update only: no first-install selection, model publication, macOS build,
live installation or service restart. Update store servers before registers.
Confirm the live Rosehall correction and screen refresh after installation;
physical printer/drawer/NFC and real-phone acceptance remain separate.
