# Combined Windows release verification — 2026-10-17

User requested publication of all pending updates from the SmartStock chats. Reused the exact in-progress releases from Add passport photo workspace rather than creating competing artifacts under the same versions.

Versions: SmartStock 1.1.42 (build 101042); SmartStudio 0.1.40 (build 1040). Verified the packaged Studio class bytes for ImageFormattingToolbar, BarcodeFormattingToolbar, BusinessCardsPanel, BadgeCardsPanel, TemplateWorkspace, PublisherNativeShapes and PublisherDrawingText match the latest compiled source. Server and Studio core dependency contain identical class bytes. Published release notes include the barcode/image controls and payment-after-delivery correction.

Gates: SmartStock 1074 tests, 72 skipped, zero failures/errors. SmartStudio 116 tests, zero skips/failures/errors. Website worker 47 tests passed. Security and whitespace checks passed. Server update archive is model-free and hosted model catalogue verified. Other chat completed extracted installer/native startup, queue layout, mouse-wheel and accelerated-retention checks. Actual phone/NFC/printer acceptance and live server upgrade remain separate checks. Installed SmartStock was left untouched; Studio 0.1.40 installed by the passport workspace chat.

Exact artifacts:
- SmartStock update: 167045686 bytes, SHA-256 468f76c268751363ad41970bf3640d85cd5a5310a09bda2c7aaa6f23462898b9.
- SmartStock all-in-one installer: 580120167 bytes, SHA-256 19edca2107f25173474e8e1cf17740e8bc56257f86c60d25be1bdd82a5302e14.
- SmartStudio installer: 225287310 bytes, SHA-256 986f8c2d12073977a41b7926e5871b00fa69b9c8fbd22af39f79b4d57ffb967e.

Release metadata was independently read back and verified against exact path/version/build/size/SHA. Studio public installer was fully downloaded and verified. Server first-install publication completed and its verified public selection points to 1.1.42. The independent full-download check is recorded in tmp/publish-all-public-stock.log. Update the store server before using new photo queue endpoints. No macOS package prepared.
