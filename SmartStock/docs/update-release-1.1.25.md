# SmartStock 1.1.25 Windows

Build 101025 adds arrow-key movement to Publishing Workspace. Click an object on
the canvas; arrows move it by 0.1 mm and Shift + arrow by 1 mm. Selected groups
move together, locked objects remain fixed, and movements support undo/redo.
Text-entry controls retain their normal keyboard navigation.

Validation: full Maven packaging suite 977 tests, zero failures/errors, 58
skipped. The two optional Swing editor tests passed separately with a graphics
environment, including repeated nudges, lock handling and undo. All 33 Studio
tests pass. Bash security and whitespace checks pass. The model-free ZIP and
hosted immutable model catalogue checks pass. Exact installer extraction and
native startup pass under an isolated user home. Its 24 updater payloads match
by SHA-256 and the bundled PostgreSQL installer matches the pinned hash. The
exact extracted app JAR and Java runtime pass arrow-movement and undo checks.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 149198323 | b86d751b298f393c9be37f573b269d0e3b79f855994dc45d2bab7c335c3dc167 |
| Installer | 562570374 | 53997d4c010fad875d7a14fb49a01e86da87b9dd87e655265ad92131c9699e74 |
| Migration bridge | 3318 | 57a29d3da367bafb00597655af30067bbb2ac221a3426789cac4a6413b482e0b |

No running store, installed app, database or Windows service was changed.
Actual installer setup/service, live LAN and update/rollback acceptance,
macOS, physical printers/duplex/barcodes, drawer/NFC and real Publisher conversion
remain separate checks. Update the store server before registers.

Publication evidence is saved under `target/release-windows-1.1.25`.

The update ZIP and installer are published. Complete hosted downloads matched
their exact local sizes and SHA-256 hashes before publication. Update metadata
readback matches version, build, platform, bucket, path, size, hash and published
status. Installer manifest readback matches the verified artifact. Public HEAD
returns HTTP 200, the 1.1.25 filename and 562570374 bytes.

Download: https://downloads.deckers.gy/download/windows
