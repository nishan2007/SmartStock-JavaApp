# SmartStudio 0.1.94 Windows quick update

Build 1094, prepared 3 November 2026 from the combined shared workspace with
the exact SmartStock 1.1.104 client dependency.

- Updater: `SmartStudio-update-0.1.94.exe`, 173280942 bytes.
- SHA-256: `a87283b8e95cde7979f212091c069f4ffc63347d151ee781bcd49c97a6a25949`.
- Full Maven suite: 344 tests, zero failures/errors, one skipped.
- Standard Windows packaging native startup check passed.
- Installed the exact updater over an isolated older app image; exact Studio
  and SmartStock JARs, native startup, runtime preservation and absence of model
  weights verified. Desktop/Start Menu shortcuts restored and their targets
  verified afterward. Live application files were not updated.
- The first isolated installation encountered read-only files inherited from
  the old jpackage image. Clearing that attribute on the isolated copy allowed
  the installation and launch checks to pass; production files were untouched.

Retains Canon direct-print fitting and the current template, publication/PDF,
barcode, numbering, passport and print/cut changes. Save drafts before updating;
update the store server first. Published 3 November 2026 after full hosted
download and size/SHA-256 verification. Independent metadata read-back confirmed
version, build, platform, published status, object path, size and hash. Physical printer/cutter,
paired store and macOS checks remain separate. No live installation was made.
