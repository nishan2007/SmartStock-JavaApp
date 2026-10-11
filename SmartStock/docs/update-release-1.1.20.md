# SmartStock 1.1.20

Windows build 101020. Area-priced orders bill the configured maximum material width
times entered length, preserve actual design dimensions, and save Portrait/Landscape
orientation in design details. Retains current SmartStock workspace changes.

## Validation

- Maven packaging suite: 918 tests, zero failures/errors, 52 skipped.
- Repository security and whitespace checks passed.
- Update archive passed model-free/layout/version and hosted model catalogue checks.
- Exact installer payload extracted successfully. Its application JAR matches the
  update ZIP and tested Maven JAR byte for byte; no ONNX model weights were found.
- Exact extracted native Windows launcher started successfully using its bundled
  Java 17.0.20 runtime and an isolated user home/development profile. Startup log
  confirmed the welcome window was visible. Only this test process was stopped.
- Full setup wizard/service installation, live custom-order/database flows,
  production slip printing, hardware, update/rollback and macOS checks remain
  outstanding. No live store installation or Windows service was changed.

## Exact artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 133349874 | 6c0f5726907fe8991b832b1c7855acbe5f525c3e512eae96ffb756b657cde1e5 |
| All-in-one installer | 546695723 | d83841a3f04897a206c32d2bf144c52e6155ac7c6de53bdda379f822450da346 |
| Model migration bridge | 3318 | 3967f4c62ec0e8640eef9f5724ed6e66599a614090993f04b68d40ef7e60d997 |

## Publication

Windows updater and all-in-one installer are published. Full hosted downloads matched
the exact local sizes and SHA-256 hashes before release metadata and permanent
installer selection changed. The published update row was read back and matched
version, build, platform, object path, byte size, SHA-256 and published status.
The public installer HEAD returned HTTP 200, the 1.1.20 filename and 546695723 bytes.

Download: https://downloads.deckers.gy/download/windows

Update the store server first, then registers.
Models remain separate from application packages.
