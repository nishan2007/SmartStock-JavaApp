# SmartStock 1.1.66 cumulative Windows release

Build 101066, published 21 October 2026 (America/Guyana).

Packages the current application work across chats, including prior overseas
orders and dashboard links, sale search corrections, saved photo records and
shared publication/transparency rendering. SmartStudio 0.1.60 ships with the
identical 1.1.66 core JAR and adds the local Bill Books workspace and import fix.
Previously deployed web applications were not redeployed by this app release.
Customer templates and unfinished conversion/font/layout review are separate.

Validation: Maven package passed, 1179 core tests, 96 skipped, zero failures or
errors; all 201 Studio tests passed. Security and whitespace checks passed.
Model-free update, hosted model catalogue, extracted installer JAR identity and
isolated native launcher acceptance passed. Both accepted installers contain
the identical shared core JAR. All 996 source inputs matched the release snapshot.

The in-app update and all-in-one installer are published. Hosted bytes were
fully downloaded and verified before metadata or selected installer changes.
Release metadata was independently checked against version, build, immutable
object path, size and hash. A complete independent public installer download
matched the accepted bytes. Studio's metadata and public download also matched.

Update: 167301026 bytes
SHA-256: 52a86bed755469d8e45d84e224cf8cfe307b23703b53c93438d21ec985bf865c
Installer: 580364826 bytes
SHA-256: 87af81b42a9842bd955822d8dbe2d1cb20c92fc736bb191ec8b6654790fd08b0
Migration bridge: 3318 bytes
SHA-256: f6f8499d0547da8134d2eb60973dcb702882b938dbd90ceafd77ebdb4947052f

Evidence: target/release-windows-1.1.66 includes package/security/verification/
acceptance/publishing logs, published-release.json, source-snapshot.json,
artifacts.json and public-downloads.json. Source changes remain unstaged.

No running store was updated or restarted. Update the store server before
registers. Installed upgrade/rollback, live database/service operations and
physical printer/drawer/NFC checks remain separate acceptance steps.
No macOS artifact or AI model weights were built or published.
