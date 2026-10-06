# SmartStock 1.0.243 Windows release

Includes the current application changes: permission-controlled manual custom-order items and print add-ons, saved item/add-on details on order views and slips, Ethernet receipt routing for drawer closing/reprinting and returns, and quotations supporting either discounted unit prices or percentage entry. Discounts carry into invoices. The release also retains existing startup, permission-catalog repair, updater, and document improvements.

Built from the frozen source at D:/SmartStock-release-1.0.243. Source manifest: D:/SmartStock-source-manifest-1.0.243.json. Artifacts: D:/SmartStock-package-1.0.243.

Validation: frozen-source Maven package ran 826 tests with zero failures/errors and 44 optional skips. Repository security check and git diff --check passed. Windows ZIP layout and pinned bundled tooling checks passed.

Update: smartstock-windows-1.0.243.zip; build 100243; 1,114,081,923 bytes; SHA-256 aca5c5c488c08e7b3c8b8728ef37969b243a3eb66385f002405b9d7ed5d53430.

Installer: smartstock-windows-setup-1.0.243.exe; build 100243; 1,520,391,527 bytes; SHA-256 5f1139394007c5af1209bad437304367cf65d485ac80c00d5c07ffa7037c8da4.

Isolated Windows fixture checks passed: packaged updater window remained responsive with closing disabled; every installed update payload file matched and both native launcher configurations used 1.0.243. Exact installer extraction matched all 25 update payload files and the pinned PostgreSQL installer hash. Both the updated app fixture and the extracted installer native app opened responsive Welcome windows using bundled Java 17.0.20 and isolated homes. No production installation registry or services were changed.

Update published successfully after full download verification of its exact size and SHA-256. Latest Windows update metadata was read back and verified as 1.0.243 / 100243 against the same artifact. Installer publication also completed after full download verification of its exact size and SHA-256. The publisher verified the returned latest-installer manifest against the same artifact. The initial multipart upload failed; its successful retry was published only after verification. Windows installer: https://downloads.deckers.gy/download/windows.

Physical Ethernet printing, printer/drawer/NFC hardware, live store transactions and database flows, service upgrades, backup/restore, and macOS installation require separate verification. No production store transactions were performed as part of packaging.
