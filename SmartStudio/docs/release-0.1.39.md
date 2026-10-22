# SmartStudio 0.1.39 Windows release

Adds mouse-wheel zoom over the shared photo framing preview and zoom slider.
Scroll up to zoom in and down to zoom out. Slider/percentage remain synchronized;
fractional scrolling accumulates for smooth trackpads. Existing zoom limits and
photo position/rotation are preserved. Includes passport pictures and other
workspaces using the shared framing dialog.

Validation: 107 Studio tests passed, zero failures/errors/skips. Repository
security and whitespace checks passed. Extracted the final Inno installer into
an isolated acceptance directory; main and 1.1.41 dependency JAR hashes match
build inputs. Extracted native launcher startup passed. Dispatched wheel events
against the packaged classes to verify zoom direction on preview and slider,
fractional movement, upper/lower limits, and position/rotation preservation.
No ONNX model weights are included; runtime and notices remain present.

Windows installer: `SmartStudio-setup-0.1.39.exe`, 225226790 bytes.
SHA-256: `609f6723bfdcb1521e60dfa2e8e63a0373a8c44d1c134191e777c6736b9a33de`.
Build: 1039. SmartStock client dependency: 1.1.41.

Published to the SmartStudio Windows channel on 2026-10-17 after full hosted
download size/SHA-256 verification. Release metadata was read back and matched
version, build, published state, size, hash, and object path:
`smartstudio/windows/0.1.39/609f6723bfdcb1521e60dfa2e8e63a0373a8c44d1c134191e777c6736b9a33de/SmartStudio-setup-0.1.39.exe`.

Physical mouse/trackpad interaction and photo printing remain unverified.
Full interactive installer execution and live installation/update acceptance
remain outstanding; extraction/launcher checks do not prove those behaviors.
No live store installation or service was updated/restarted. No macOS build.
