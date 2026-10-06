# SmartStock Windows update 1.0.231

Prepared on 2026-10-01. Windows x64, build 100231. Update-only release; no installer was created or selected for publication.

## Contents

Built using the full current source tree, including tracked and untracked application resources and database migrations selected by Maven. Includes staff background removal, Company Preferences branding, offline photo model, custom-order customer items/media/design, storefront resources, and the current register, payroll/time-clock, sync and recovery code. Website worker deployment is separate from this application update.

## Artifact

- ZIP: target/release-windows-1.0.231/smartstock-windows-1.0.231.zip
- Size: 296,613,340 bytes
- SHA-256: 83a467b1a23a4389a0ea94d2b52719c68817e6f66178a50834c69e390d0f0a3b

## Validation

- Existing Windows packaging script with the new -UpdateOnly option completed Maven package and tests: 761 tests, zero failures/errors, 33 environment-dependent skips.
- Repository security check, JavaScript syntax, PowerShell packaging syntax and git diff --check passed.
- Extracted the final ZIP into an isolated verification folder and checked the packaged background-remover resources against source.
- Used the final release JAR and its bundled ONNX model/dependencies to verify offline transparent PNG output, original dimensions, preserved transparent pixels and existing studio JPEG output.
- No installer EXE was produced in the release directory.

## Live acceptance

No installed application, production database or Windows service was upgraded. Browser activation/sign-in, branding, upload/download, real-photo quality, database migrations/save/reload, backups/recovery, printers, drawers and NFC acceptance remain outstanding. No Mac package was built.

## Publication

Published successfully. The R2 object was downloaded and verified against the local size and SHA-256 before release metadata was inserted. A subsequent production feed read confirmed 1.0.231 / build 100231 is the latest published Windows release with the expected size, hash and content-addressed artifact path. The installer portal was not updated.
