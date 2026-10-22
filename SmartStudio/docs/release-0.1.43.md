# Windows publication — 17–18 October 2026

SmartStudio **0.1.43 / build 1043** and SmartStock **1.1.45 / build 101045** add persistent linked Riso sub-pages. Each sub-page references whole editable objects in its main page, so movement, text and style changes flow through without copying artwork. New objects remain Unassigned until organised or excluded. Picture-area splitting is deferred.

Update the server first, then all editing clients before saving linked templates. Linked templates use publication format 2; older clients reject them to prevent losing memberships. Existing format-1 templates remain supported. No database schema migration is required.

Validation: SmartStock 1,081 tests, 72 skipped, no failures/errors; SmartStudio 147 tests, no failures/errors. Security and whitespace checks passed. Linked membership, main-object changes, undo, page navigation, archive persistence, shared-template storage and grayscale PDF output have automated coverage. The organiser was rendered and visually checked.

Publication does not install updates or restart running applications/services. Physical Risograph/printer output, live server/database saves, NFC hardware and skipped database integration checks remain separate acceptance steps. No macOS release was built.

Downloads: [SmartStudio Windows](https://downloads.deckers.gy/download/smartstudio/windows), [SmartStock Windows](https://downloads.deckers.gy/download/windows).


| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| SmartStudio-setup-0.1.43.exe | 225386202 | `349d2379c7c8515b909914b4002d7f4b8da5e1044701e960605c0ee73542016f` |
| smartstock-windows-1.1.45.zip | 167049001 | `ebc407fa0fb195f02d7f7a5d417acda2b5184c423dd56ad3ed641a400bea57ca` |
| smartstock-windows-setup-1.1.45.exe | 580113202 | `329e4a8fe9c754de54ea331e613ad4c73f15cade4dd60a65cbe6b9e218c20851` |
| smartstock-ai-model-migration.jar | 3318 | `7b4889e269740beacaa98a75980a238df41720661da9d815989862eae992d35b` |

Extracted installers passed isolated native launcher checks. SmartStock installer/updater JARs match exactly. Every entry in the SmartStudio core dependency matches the packaged SmartStock release; JAR container timestamps differ after packaging retries. Packages contain no model weights. The hosted model catalogue passed publisher validation.

The Windows packager now writes the launcher icon path with forward slashes so Java properties do not interpret backslashes as escapes when building on another drive. Build output moved to D: after C: ran out of space; no store files were removed.

Published both releases. Publishers verified full hosted artifact size and SHA-256 before changing release metadata or installer selection. Final public downloads of both installers matched exact accepted sizes and hashes; release rows matched version, build, size, hash, bucket and immutable object path.
