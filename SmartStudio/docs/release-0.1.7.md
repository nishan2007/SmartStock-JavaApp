# SmartStudio 0.1.7 — Windows build 1007

Published installer includes Image Studio removal/enhancement controls, local 2x/4x
server processing, detailed previews, native image-drop lifecycle correction and
the current Badge Cards/template workspace. SmartStock client dependency: 1.1.19.

Validation: all 27 SmartStudio tests passed. The exact installer was extracted;
its application JAR matched the tested Maven JAR and contained no model weights.
The extracted native launcher passed startup with an isolated user home. Security
and whitespace checks passed. Actual WhatsApp dragging, a live paired employee
session, full installer/updater handoff and physical printing remain manual checks.
No installed SmartStudio or store service was changed.

`SmartStudio-setup-0.1.7.exe`: 138182450 bytes, SHA-256
`a2474f23358f02e7d581c92bcb62a5b9ed274ca5e75fed548a13d0d57f8ed96d`.
Portable ZIP: 140531140 bytes, SHA-256
`73fa9c00c2ffba2c16e5b7810d0b43886f6fccd18531cdf5291d3726170715b6`.
Only the installer was published to the SmartStudio update channel.

Hosted installer download matched the complete local byte size and SHA-256 before
metadata publication. Published metadata was read back and matched version,
build, Windows platform, exact object path, size, checksum and published status.
Update the store server to SmartStock 1.1.19 first, then install/update SmartStudio.
Enhancement requires the separate administrator-installed model; sign in again
after installing it to refresh availability.
