# SmartStudio 0.1.71 Windows update

Build 1071, 23 October 2026, SmartStock client dependency 1.1.80.

Adds visible Add/Delete page actions, automatic or fixed-width zero padding with a live preview, and persistence of padded numbers in template records. Removes the repeated workspace heading, aligns tabs with Save/Print/Export PDF, and uses rounded purple output actions. The floating Layers panel now follows the selected reference: rounded card, soft lavender selection, outline icons, subtle dividers, compact footer and slim scrolling for long templates. Existing visibility, ordering, deletion and undo behavior remains available.

Update-only executable for an existing installation. No Java runtime or AI model weights; inference runtime and licences retained. No first-install portal change, store-server update, phone-page deployment or live installation.

Artifact: `SmartStudio-update-0.1.71.exe`, 169,732,687 bytes.
SHA-256: `f7f4469bca329045b044e4e26c5ba27429f46db47bb99a79fcc93da72ab4b2da`.

Validation: 277 Studio tests, one skipped, no failures/errors. Security and whitespace checks passed. Hosted model catalogue readiness passed. Extracted Studio/core JARs match build outputs, required icon resources are included, and no model weights/runtime are bundled in the update. Exact native launcher and ribbon renders passed in an isolated profile. Applied the executable to an isolated 0.1.70 copy: old application replaced, Java runtime preserved, native launch passed. Native Layers tests cover selection, scrolling, visibility, ordering and deletion; a synthetic reference preview was visually reviewed. Physical printing, live server/phone operation, manual DPI/accessibility and rollback remain separate verification steps.

Evidence: `SmartStudio/target/release-0.1.71-*`, `acceptance-0.1.71-update`, `update-install-0.1.71`, and `layers-reference-preview.png`. Protected publisher credentials remain outside Git.

Published successfully. The complete hosted executable was downloaded and matched the exact size and SHA-256 before metadata publication; independent metadata verification matched version, build, platform, object path, size, hash and publication status.
