# SmartStudio Windows installer 0.1.0

Built on 2026-10-01 as `target/SmartStudio-setup-0.1.0.exe`.

- Size: 137,391,905 bytes
- SHA-256: `2363c9009d0308424e1befa8bab665a69236321d9fff94afc673f0793a530837`
- Includes the Java runtime and existing Deckers application icon.
- Installs for the current Windows user and offers a desktop shortcut, with a
  Start menu shortcut and normal Windows uninstall support.
- SmartStock server files, services, and database configuration are not installed.

Verified by installing the exact setup executable into an isolated workspace
folder, comparing the installed application JAR against the build, checking that
only the 1.0.234 server client library is present, and running the installed
native executable with its bundled Java to render the staff login screen.
The temporary installation was then uninstalled. A normal interactive install
and live pairing/login/removal remain separate acceptance steps for the user.

SmartStock 1.0.234 must be applied on the store server before connecting.
