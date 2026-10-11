# SmartStock 1.1.15 — Dashboard loading and file-picker drops

Fixes the low-stock UNION query alias that prevented Orders Dashboard from loading. A read-only check against the production store reproduced the undefined-column error before the fix and loaded the full dashboard after the fix (10 action rows, 80 audit entries).

Adds file drops inside the Add Files picker. Drops select files and approve the picker; existing validation and upload handlers process the result. Picker navigation reinstalls drop handlers on replacement controls. Tests verify selection and approval behavior, but a real Explorer drop on the deployed application remains outstanding.

Security and whitespace checks passed. Installed upgrade, desktop file drop, live Windows services, database mutations, printers, cash drawer, NFC and rollback remain separate checks. Update the server before registers.

Full Maven packaging suite: 892 tests, zero failures/errors, 47 skipped. Model-free archive verification passed.

The exact installer was extracted; its application JAR matched the tested Maven build by SHA-256 and its native launcher/runtime opened the welcome screen with an isolated development profile. The smoke process was stopped. This is a packaged-launcher check, not a production installation or real Explorer drag-and-drop test.

Final artifacts: ZIP 133,323,762 bytes, SHA-256 `c624dc67a2f9d44cfe81b94d322a6521c69a5f2e31e70aeb42cdd205b773c846`; installer 546,668,560 bytes, SHA-256 `83b972409efa1cc8d44dabdc200b5d0858bcbfdaad12390024cef005848dd72a`. Build 101015.

Publication completed: complete hosted update and installer downloads matched local size and SHA-256 before metadata/latest selection changes. Windows update 1.1.15 build 101015 is published; public installer HEAD returned HTTP 200 with the expected filename and size. Download: https://downloads.deckers.gy/download/windows
