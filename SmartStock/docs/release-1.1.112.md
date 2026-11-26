# SmartStock 1.1.112 — Windows quick update

Build: 101112. Update ZIP only; first-install selection is unchanged.

Fixes the mobile Custom Order Spoils recording section and exposes shirt size, colour, variant, dimensions, customization, instructions, print add-ons, and line status.

Validation:
- Maven package/full test suite: 1,472 tests, zero failures/errors, 158 skipped (including environment-dependent database tests).
- Four spoil web regression tests and mobile browser preview with synthetic order data passed.
- Repository security check and git diff whitespace check passed.
- Exact model-free ZIP verified, retaining inference runtime and licences.
- Exact update applied over an isolated 1.1.111 installation; installed JAR, launcher configuration, native welcome startup, runtime preservation and profile/model preservation passed.

Artifact: `smartstock-windows-1.1.112.zip`, 169070970 bytes.
SHA-256: `f7c9912334beef9053d3402aa213195ea5b6e685bbd4d2b3f30c1e819b38c442`.

No live store installation, service restart, live spoil submission/inventory deduction, physical phone QR/camera, printer, cash drawer or NFC check was performed. Apply to the store server before registers; phone pages must reload the updated server assets.

Publication completed: the hosted ZIP was downloaded and its exact size/SHA-256 verified before publication. Published metadata was read back and matched version 1.1.112, build 101112, object path, size and SHA-256.
