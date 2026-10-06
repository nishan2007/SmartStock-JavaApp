# SmartStock 1.0.244 Windows release

Fixes discounted quotation prices changing on save. The quotation cart no longer parses a discount percentage as whole-dollar money. Quotation and invoice monetary calculations preserve two decimal places, calculated percentages preserve fractional precision, quotation server price-approval comparisons retain cents, and quotation/invoice documents display cents. Discounted-price entry rejects more than two significant decimal places.

The existing custom-order label test was updated for the current separate receipt/label count workflow, including zero-label printing after receipts.

Frozen source: D:/SmartStock-release-1.0.244. Source manifest: D:/SmartStock-source-manifest-1.0.244.json. Artifacts: D:/SmartStock-package-1.0.244.

Validation: 829 tests, zero failures/errors, 44 optional skips. Security check and git diff --check passed. ZIP layout and pinned bundled tooling checks passed.

Update: smartstock-windows-1.0.244.zip; build 100244; 1,114,083,094 bytes; SHA-256 2b464bec5cc2f4df7511ea67f6ad28cb4a4a53b7de4a175abe313611fa462a4b.

Installer: smartstock-windows-setup-1.0.244.exe; build 100244; 1,520,449,681 bytes; SHA-256 c6481b33cb6015bbfb5b118808461ac5443e3d5330b135af10702ad2cbafc856.

Isolated Windows checks passed: updater window remained responsive with closing disabled; every installed update file matched and both launcher configurations used 1.0.244. Exact installer extraction matched all 25 update payload files and the pinned PostgreSQL installer hash. The updated native app and extracted installer native app both opened responsive Welcome windows using bundled Java 17.0.20 and isolated homes.

Update published after full download verification of its exact size and SHA-256. Latest Windows update metadata was read back and verified as 1.0.244 / 100244 against the same artifact. Installer also published after full download verification of its exact size and SHA-256. The publisher verified its returned latest-installer manifest against the same artifact. Windows installer: https://downloads.deckers.gy/download/windows.

Live quotation/invoice transactions, database flows, backup/restore, service updates, physical printer/drawer/NFC checks, and macOS installation require separate verification. No production store transactions or production installation registry changes are included in this packaging verification.
