# SmartStudio 0.1.22 — WordArt and NFC employee login

Business Cards and Labels have Add WordArt and Edit WordArt commands under
Template & insert. Select a text layer to apply or change plain, arch up,
arch down or wave lettering, bend, font, bold/italic, fill/gradient, outline
and shadow. Text, merge fields, position, dimensions and rotation remain
editable. The shared publication renderer uses vector glyph paths for canvas
and direct printing; the existing PDF exporter rasterizes sheets at 300 dpi.
The SmartStock Publishing workspace also exposes the shared WordArt editor
in source. The running SmartStock installation was not changed or restarted.

Imported Publisher lettering can be styled manually with these controls.
Automatic reconstruction of every Publisher envelope/effect is not provided,
and exact Publisher fidelity remains unverified.

Staff sign in has a Tap NFC employee badge command. It uses the existing
PC/SC reader implementation and encoded SmartStock badge payload, never a card
UID. Capture runs off the Swing event thread with a 20-second card timeout.
The badge identifier is kept out of the visible username field and diagnostic
description. The paired store server validates the badge, checks PIN policy,
authenticates the staff member and enforces Studio permissions. Required
first-use employee PIN setup uses the existing server endpoint and account
password. PIN/password buffers are cleared. Username/password login remains
available through its own button. No server schema changes were needed.

Verification:

- All 67 SmartStudio tests passed, including reader absence, invalid card/UID,
  normalized payload, server policy rejection and private capture description.
- Targeted publishing, WordArt, barcode, PC/SC and NFC login architecture/privacy
  tests passed. WordArt tests cover distinct curved rendering, editable archive
  round trips and invalid effect bounds.
- Repository security check and git diff --check passed.
- Rendered WordArt preview visually inspected. Packaged and installed launcher
  startup checks passed; installed login screenshot includes the NFC controls.
- Windows device enumeration and Java PC/SC both detected no compatible reader.
  Physical tap, live employee/PIN setup/login and printer output remain unverified.
- Installed alongside previous versions at
  C:/Users/decke/AppData/Local/Programs/SmartStudio-0.1.22 with a versioned desktop
  shortcut. Existing template library is retained. SmartStock processes 24360
  and 25296 retain their October 15 start time. No release was published.

## Final Windows publication

The final release was rebuilt against SmartStock 1.1.35; its 67-test gate passed.
The exact installer was extracted and its SmartStudio and SmartStock dependency
JARs matched the build inputs. Its extracted native launcher passed startup.
All 41 packaged JARs and loose files were checked for excluded model weights.
The initial local installation described above preceded this release rebuild.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Installer | 225105312 | a621e76372c38c199720837c77f3eb4f1da465a63cc1826b0e22a6f7ea854126 |
| Portable ZIP | 234084704 | 8ba06c29676555c202c8e6e3223daf01889c0c08215fd712091c0cf89f8243c4 |

The protected publisher downloaded and verified all installer bytes before
publishing version 0.1.22, build 1022. Metadata readback matched the version,
build, exact size, SHA-256 and content-addressed object path. The public portal
selects that installer; every unrelated deployed binding was verified unchanged.
A complete public download matched the same size and SHA-256.
The portable ZIP remains available locally.

https://downloads.deckers.gy/download/smartstudio/windows
