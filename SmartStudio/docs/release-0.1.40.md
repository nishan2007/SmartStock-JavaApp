# SmartStudio 0.1.40 Windows release

Released 2026-10-17, build 1040. Adds shared Photo Queue and internet phone
capture for passport pictures. Requires SmartStock server 1.1.42 and the deployed
website routes. Staff confirm physical print success before the immutable
24-hour deletion deadline begins.

Installer: `SmartStudio-setup-0.1.40.exe`, 225287310 bytes.
SHA-256: `986f8c2d12073977a41b7926e5871b00fa69b9c8fbd22af39f79b4d57ffb967e`.

107 SmartStudio tests passed. Final installer extracted; main and selected
1.1.42 client JAR hashes matched build inputs. Extracted launcher, wheel zoom,
and queue layout checks passed. Hosted installer fully downloaded and checked
before metadata publication. Metadata read-back matched exact version, build,
object path, size, and SHA-256. Public Studio installer selection was updated
only after verification.

Installed here using the accepted Inno installer after the previous SmartStudio
window closed gracefully. Installed main/client hashes match; the installed native
launcher passed. SmartStudio was reopened. SmartStock processes/services were
not updated, stopped, restarted, or installed.

Website Worker deployed with protected production config, retaining browse-only
shopping mode. Worker version: `09bf1772-5fbe-4ae9-a998-c65e33b02354`.
Both phone pages returned 200 with privacy/no-cache headers. Unsigned requests
were denied. All 47 Worker tests pass.

Actual phone-camera/mobile-data delivery to the installed server and physical
printing remain outstanding until the operator updates SmartStock to 1.1.42.
Shared-read and 24-hour cleanup acceptance used isolated test folders and an
accelerated clock against exact packaged server classes. No macOS build.
