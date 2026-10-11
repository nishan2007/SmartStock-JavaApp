# SmartStudio 0.1.8 Windows

Build 1008 adds Paste image and Ctrl+V in Image Studio, registers Windows native
PNG transfers before incoming drops, and falls back to other supplied image
formats when an advertised file or bitmap cannot be read.

Installer: SmartStudio-setup-0.1.8.exe, 138185386 bytes.
SHA-256: 55d4cb6d7b218bcae5e2af2ac0ec85d14a4368a0eeda42293163eb71c7433a3b.

All 28 SmartStudio tests pass, including a source that fails its advertised file
and bitmap formats but supplies a valid PNG. The repository security and
whitespace checks pass. The installer was extracted: its SmartStudio JAR matches
the Maven artifact, contains no ONNX model weights, and its native launcher passes
the startup check with an isolated profile. Image Studio was visually checked at
820 pixels, including the Paste image button.

Actual WhatsApp Web dragging, browser Copy image, clipboard pasting, the installer
wizard, and upgrading a live installation remain manual acceptance checks. The
application cannot fetch browser-only blob references. Use Copy image followed by
Paste image when a Web drag does not supply image bytes; download and Choose image
remain available. No live application installation was changed.
