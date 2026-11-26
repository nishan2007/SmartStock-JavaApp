# SmartStock 1.1.108 Windows release

Build 101108, prepared 5 November 2026 from the current shared workspace. Preserves all pre-existing uncommitted work and includes completed changes across chats: searchable customer selection; Phone Minutes native Split Workspace and approved-browser Customer First UI; stale remote-sync save/reversal fix limited to unlimited minutes; existing payments, account statements, receipt reprints, drawer accounting and paid-credit release; Work Queue Display version/manual update controls and Windows updater SID repair. Existing published storefront camera/brightness work is retained. SmartStudio 0.1.99 uses the identical 1.1.108 client JAR and is distributed separately.

## Exact artifacts

- Update ZIP: 169046383 bytes, SHA-256 e9cd20c2f092cfe40179dcf5d073bda1fd1ed0245cd2702bba74748ff93f4b40.
- All-in-one installer: 582115320 bytes, SHA-256 4e3cb785b00be87eb97c3503ab0df533d95dca0a08b824103f4034a526f98fdc.
- Model migration bridge: 3318 bytes, SHA-256 437bdebadf590f65849970595db7559cecdaebbf89749d2c3577840c143f0bf1.
- ZIP wrapper of the exact installer placed in OneDrive - DECKERS/Desktop/DECKERS_SK/smartstock-windows-installer-1.1.108.zip. Embedded EXE and copied ZIP hashes verified. Prior versions preserved.

## Validation and publication

Maven package/full tests passed: 1461 recorded tests, zero failures/errors, 157 skipped in the normal headless/environment-dependent gate. Native Phone Minutes layout/search checks also passed explicitly before packaging. Real authenticated LAN and employee HTTPS integration tests use disposable PostgreSQL, verify persisted money changes and retries, and leave live accounts untouched. Repository security through Git Bash, customer gateway tests (3), model-free update checks, hosted model catalogue, source whitespace and 1261 unchanged release inputs passed.

The exact ZIP was applied over an isolated 1.1.107 installation, with matching final JAR, refreshed launcher, retained runtime/profile sentinel, no model weights, required minutes classes/assets/migration and successful native startup. Exact installer extraction verified matching JAR, model exclusion and native startup. No live installed application, service or database was modified.

Published update metadata was independently read back and matched version, build, platform, size, content-addressed path and SHA-256 after complete hosted byte verification. Customer web gateway b7dd634a-22e0-4251-a50d-7a98ae3865be deployed with existing origin and service bindings retained. Hosted minutes HTML/CSS/JS/font/logo match local bytes; unapproved requests and foreign origins are rejected.

Full first-install selection published after complete hosted size/SHA-256 verification. Independent public-link verification at 2026-11-05T20:18:42.162Z returned HTTP 200, 582115320 bytes and SHA-256 4e3cb785b00be87eb97c3503ab0df533d95dca0a08b824103f4034a526f98fdc. Public installer: https://downloads.deckers.gy/download/windows.

## Rollout and outstanding checks

Update the store server before registers and Studio clients. Mobile minutes requires the new server. Use the full installer as administrator to repair older protected Windows updater scripts. Local minutes/browser retry migrations are included; cloud snapshot-reader/permission migration rollout remains separate from binary publication. Model weights remain outside packages and existing active-profile models are preserved.

Live Windows services, real production database/drawer acceptance, physical receipt/C5240 printers, NFC, physical iPhone capture/colour quality, deployed rollback and macOS builds remain separate outstanding checks. Publication does not install updates. No Git staging, commit or push was performed.
