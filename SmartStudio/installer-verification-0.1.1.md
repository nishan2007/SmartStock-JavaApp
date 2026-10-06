# SmartStudio Windows installer 0.1.1

Built 2026-10-01 as `target/SmartStudio-setup-0.1.1.exe`.

- Size: 137,408,936 bytes
- SHA-256: `a7337036a762fc30b65d71be8c5eaf23afea34a20a99c22adca6b4bafbd3b313`
- Requires SmartStock server 1.0.235 for physical-device linking and Allow Studio.
- Includes Java, existing Deckers icons, and desktop/Start menu shortcuts.

Network files are read directly into bounded memory without a separate file-size
metadata lookup. PNG/JPEG decoding uses an in-memory image stream. Missing or
unreadable files produce a visible error dialog with network/read-access guidance;
loading/removal show progress. Selecting another image clears the old image/result.

Four native tests passed, covering independent client identity, file names with
spaces/Unicode, PNG bytes/dimensions/alpha, missing-file messages, and input bounds.
The installed 0.1.0-to-0.1.1 upgrade was verified in a temporary folder, including
cleanup of stale JARs. The exact final 0.1.1 installer was installed separately,
its JAR matched the build, and its native executable loaded a PNG asynchronously,
rendered the preview, and enabled Remove background using its bundled Java.
Temporary verification installations were uninstalled.

The user's remote network share was not available for this verification. Retry
that PNG after installing the new version; the app now reports an actionable
message if the share cannot be read.
