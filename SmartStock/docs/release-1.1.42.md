# SmartStock 1.1.42 Windows release

Released 2026-10-17, build 101042. Supplies authenticated Studio phone capture
and local shared photo-queue authority for SmartStudio 0.1.40. Also includes
product-reference synchronization work prepared for 1.1.41. The user reserved
installation of SmartStock for themselves. No running store application/service
was installed, stopped, restarted, or upgraded.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `smartstock-windows-1.1.42.zip` | 167045686 | `468f76c268751363ad41970bf3640d85cd5a5310a09bda2c7aaa6f23462898b9` |
| `smartstock-windows-setup-1.1.42.exe` | 580120167 | `19edca2107f25173474e8e1cf17740e8bc56257f86c60d25be1bdd82a5302e14` |
| `smartstock-ai-model-migration.jar` | 3318 | `12cddf4f44baad1f25e4246c61589faa47c529c364729a3bbaed51fb9438504e` |

Normal Windows packaging generated the model-free updater and all-in-one installer.
Update verification passed. Final installer extraction matched the application
JAR to build inputs; no ONNX weights were present. Runtime and notices/licences
remain packaged. Bundled Java executed queue acceptance against extracted classes.

Maven test/build, repository security and whitespace checks passed. Queue tests
cover persistence, shared store access, store isolation, retries, image/size
validation, expiry and cleanup. Tests against the exact ZIP and extracted installer
verified two photos, shared reads, deduplication, restart, and deletion 24 hours
after first print confirmation using an accelerated clock and isolated folders.

Hosted updater download size and SHA-256 were fully verified before publication.
Metadata read-back matched version, build, path, hash and size. Website routes
and SmartStudio are deployed/updated. The operator must update the installed
store server before using the new queue endpoints.

The all-in-one installer was also uploaded, fully downloaded, size/hash verified,
and selected as the Windows first-install download. Its published manifest was
read back from R2 and matched version 1.1.42, build 101042, size, and SHA-256.

Live mobile-data camera capture, delivery between physical computers, printer
output and installed SmartStock service/update acceptance remain outstanding.
No macOS release was built.
