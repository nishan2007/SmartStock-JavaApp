# SmartStock 1.1.12 — Order file drops and visible POS eligibility

Fixes order-file drops across child controls in the new-order attachment panel and adds drop support to saved-order Attachments. Dropped files reuse readable-file, duplicate and count validation; saved-order uploads keep the same line selection, delivered-order restriction, size approval and authenticated server handlers as Add Files.

Moves Sell in normal POS beside the custom-item name, adds a Sell in POS list column, captures the setting before asynchronous save and resets it when clearing the editor. The 18 pictured shirt items were separately enabled in the local production store; that data change is not a release migration.

Validation: full Maven packaging suite, security check and whitespace check passed. Model-free update archive verification passed. Physical Windows Explorer drops, installed-app upgrade, label printer and barcode scan, live services, receipt printer, cash drawer and NFC remain separate checks. Update the server before registers.

Added POS visibility fix: GroupedProductRows previously removed all negative IDs, although custom items/variants use negative IDs. Headers are now identified by membership in the generated header map and allocated without colliding with item IDs. Real custom rows survive capture, expansion and recapture. Added a regression test with both custom-item and custom-variant IDs.

The POS catalogue also reloads when Make a Sale opens and after custom-catalog mutations; cached catalogues expire after 60 seconds. Full updated suite: 883 tests, zero failures/errors, 47 skipped. The earlier pre-fix 1.1.12 artifacts were not published; final artifacts are rebuilt from the final code.

The actual server custom-catalogue SQL returned all 66 enabled, priced shirt variants from the local production store in a read-only check. The rebuilt installer was extracted; its application JAR matched the tested Maven JAR and its native launcher/runtime reached the welcome screen with an isolated development profile. The smoke process was stopped.

Final artifacts: ZIP 133,315,482 bytes, SHA-256 `bdde67bea1583a3ae8ee6b646d9e1b1d5d8e28a2229c420679e0904b9ac4aafd`; full installer 546,662,251 bytes, SHA-256 `7e7962ed6ec5eaa09175ce01156201c0ddbfecdd9cbbb4051b21e04afb4864fc`. Build 101012.

Publication completed: full hosted update and installer downloads matched local size and SHA-256 before release metadata/latest selection changes. Windows update 1.1.12 build 101012 is published; the public installer link returns HTTP 200 with the expected 1.1.12 filename and byte size. Download: https://downloads.deckers.gy/download/windows
