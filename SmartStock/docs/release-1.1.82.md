# Windows release 1.1.82

Published on 2026-10-25 at the user's request. Build **101082** adds Sales Tasks
for deferred-price service work and the combined **Orders → Work Queue**.
See [Sales Tasks](sales-tasks.md) for workflow and role setup.

## Verification

- The standard Windows packaging script completed its full Maven package/test gate.
- Security and whitespace checks passed. Feature validation also passed 14
  disposable PostgreSQL workflow cases and the combined-queue filter tests.
- The exact update archive passed model-free layout, version, runtime/licence,
  migration support and hosted model catalogue verification.
- The exact full installer was extracted into an isolated acceptance folder.
  Its application JAR matched the update JAR. Sales Tasks, Work Queue and their
  migration resources were present. The bundled native launcher successfully
  opened the welcome screen with an isolated development user home, then the
  test process was stopped. No production installation was started or changed.
- Both hosted artifacts were fully downloaded and checked for exact size and
  SHA-256 before publication. Update metadata and the selected installer manifest
  were read back and matched version, build, object path, size and SHA-256.
  The public Windows download returned the expected filename and length.

## Exact artifacts

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `smartstock-windows-1.1.82.zip` | 167870066 | `6d40e151b64b36cc9e3f032da103c7bc0e123093b5f04c23fee7b7198820d680` |
| `smartstock-windows-setup-1.1.82.exe` | 580933568 | `931b4efdac80b7b2caa63c088b59084eb9e9bedb0cd7fdf97fdc12c58e3b5ab1` |
| `smartstock-ai-model-migration.jar` | 3318 | `19b94d3259d04a1423620d66a33162c4e54ff129984879f7c2034e3b57016753` |

The update feed and the [public Windows installer](https://downloads.deckers.gy/download/windows)
select 1.1.82. AI models remain separately hosted. No model weights were packaged
or republished, and no SmartStudio or macOS release was published.

## Rollout checks

Update the store server before registers. Production database migration, service
upgrade, signed-in installed-app task/pickup flows, update/rollback and physical
printer, cash drawer and NFC acceptance remain separate live verification steps.
Publication did not install, restart or update a live store.
