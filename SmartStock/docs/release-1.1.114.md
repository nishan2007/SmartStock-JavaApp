# SmartStock Windows 1.1.114 — 2026-11-13

Published build **101114**. Prepared from an isolated snapshot of the shared working tree; existing unrelated edits were preserved.

## Included

- Inventory product image coverage for active products, including products without a source photo. AI photo status identifies the stored asset and its main/additional gallery placement. Separate square (1:1), portrait (4:5), story (9:16) and landscape (16:9) ad slots and needs-ad filters retain per-image references and dimensions; assignment is checked by the authenticated store server.
- Administrator duplicate review with explicit keep/merge confirmation and blockers for linked business history, other-store identities and conflicting variants. No automatic live merges.
- Archive/restore catalogue invalidation and protection against stale background loads restoring archived products in Make a Sale.
- Bundled custom-item website fixed/From/quote pricing and individual plaque prices, plus improved NFC reader diagnostics.
- Existing completed Orders exchange/payment reversal and other application work already included in 1.1.113.

## Artifacts

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| smartstock-windows-1.1.114.zip | 169144616 | 8f4aa214c6901506ae6ebb484187f04c203ba11f73a6b40a902aa96b9e77f16c |
| smartstock-windows-setup-1.1.114.exe | 582211170 | b58e1cb0ab1602534da51c97f26ca097d0cad5e30e51e3967ae640c810d4b917 |
| smartstock-ai-model-migration.jar | 3318 | b4ae9b3a82cfaf435d108158ee388e42029e6dd37b9b09bbac8f06a49abc8808 |

## Validation

- Full Maven package test phase: 1483 tests, zero failures/errors, 161 optional tests skipped. A missing sibling test fixture in the initial snapshot was corrected and the full suite rerun successfully. Artifact packaging then reused the successful test result.
- Website: 81 unit tests, TypeScript and production Vite build passed. The generated frontend is included in the snapshot's application resources.
- Repository security and whitespace checks passed. Application, test, schema, tool and installer sources remained equal to the isolated snapshot through acceptance; generated frontend resources were rebuilt only in the snapshot.
- Standard Windows all-in-one packaging passed. Updater layout/model exclusion, inference runtime/notices and hosted immutable model catalogue verification passed. No model weights changed or were published.
- Exact updater applied over isolated 1.1.113: installed JAR identity, updated launcher, prior-version backup identity, runtime/model-profile preservation and native Welcome startup passed.
- Exact full installer installed with current-user privileges in an isolated directory: installed JAR identity, model exclusion, bundled PostgreSQL provisioner and native startup passed. Existing desktop/Start Menu/startup shortcut targets point to the live application; the pre-existing user uninstall registration was restored. The first cleanup encountered a protected common shortcut; cleanup was corrected and the full isolated acceptance repeated successfully.
- Evidence, package logs, source hashes and artifact manifest remain under `SmartStock/target/release-windows-1.1.114`.

## Held back and outstanding

Ad generation and approval screens are not implemented. No ad is marked approved merely by this release. Product-photo generation/review batches are store data work, not packaged image assets. SmartStudio and SmartDrive are not separately released; the previously deferred SmartDrive cloud migration remains deferred. No cloud schema migrations were applied by this release.

Website price changes are bundled; matching hosted frontend activation and store catalogue sync remain separate work. NFC reader/service acceptance on the affected register is outstanding; diagnostic improvements do not establish a hardware fix.

No live SmartStock installation or store service was updated, provisioned or restarted. Update the store server before registers. Live database/server/register workflows, synchronization, physical printer/drawer/NFC/camera checks, administrator model downloads/offline model processing, deployed service acceptance and macOS packaging remain outstanding. Installer acceptance used a fresh isolated current-user profile and did not provision a live Windows service.

## Publication

The protected installer publisher uploaded and fully downloaded the exact 582211170-byte installer, verified its SHA-256, and only then updated the public Windows installer selection. Independent read-back of `https://downloads.deckers.gy/manifest/windows` matched version, build, size and hash. The installer is available from [the Windows download](https://downloads.deckers.gy/download/windows).

The protected update publisher verified the model-free archive and hosted model catalogue, uploaded and fully downloaded the exact 169144616-byte ZIP, and checked its SHA-256 before inserting release metadata. Independent Supabase read-back matched version 1.1.114, build 101114, Windows platform, `r2:smartstock-updates`, published state, exact size/hash and immutable object path:

`updates/windows/1.1.114/8f4aa214c6901506ae6ebb484187f04c203ba11f73a6b40a902aa96b9e77f16c/smartstock-windows-1.1.114.zip`

Existing release artifacts and metadata were not overwritten. No live application installation or store service restart was performed.
