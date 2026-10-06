# SmartStock installer release 1.0.229

Prepared on 2026-10-01 for Windows x64.

## Artifacts

- All-in-one installer: `target/release-windows-1.0.229-final/smartstock-windows-setup-1.0.229.exe`
- Installer size: 708,914,194 bytes
- Installer SHA-256: `e6b5163e4a88482e97a57be4a7755135436cb7fc30f03ddf39becbb082e3b9a9`
- Update ZIP: `target/release-windows-1.0.229-final/smartstock-windows-1.0.229.zip`
- Update size: 296,601,801 bytes
- Update SHA-256: `1c80f4d313cec3fe2a9894ba8453908f12d8727987e5de50a705c98e0c4235bc`

A matching installer copy and checksum file are in the release operator's
Downloads directory as `SmartStock-Setup-1.0.229.exe` and its `.sha256` sidecar.
The installer includes the Java runtime and the SHA-256-verified PostgreSQL
bootstrap installer. This is a first-install package, distinct from the update ZIP.

## Validation completed

- The existing Windows release packaging script completed Maven package/tests.
- The installer was compiled with the existing Inno Setup workflow.
- An isolated per-user installation completed with exit code 0.
- The installed Java runtime started and reported Java 17.
- The installed launchers, version-specific app JAR, website resources,
  PostgreSQL bootstrap file, and Java dependencies were checked.
- The verification installation was uninstalled successfully; its temporary
  per-user uninstall registration was removed.
- All 17 portal and publication tests passed.
- PowerShell and Bash publisher syntax checks passed.
- The repository security check and `git diff --check` passed.
- The update ZIP was uploaded, downloaded and verified before publishing the
  Windows app-release metadata for build 100229.
- The complete all-in-one installer was uploaded in parts and downloaded again;
  its size and SHA-256 matched the original before the private Windows latest
  selection was published. The permanent address is
  `https://downloads.deckers.gy/download/windows`.
- The custom-domain portal returns an authentication challenge for anonymous
  page and download requests. Publication endpoints deny anonymous requests.
- The existing website connector was restored; its authenticated origin health
  check passed.

## Activation and remaining acceptance

The configured store server was still running 1.0.226 during these checks.
Install 1.0.229 and restart its background server before expecting account login
at the download portal to work. Only one store website origin is currently
configured. Add each additional store's verified origin and dedicated installer
key, and update its website server, before claiming every store is activated.

Live active/disabled-user logins, account removal, all-store access, normal
interactive first-run setup, production database/service upgrade, backup/restore,
and printer/drawer/NFC behavior have not been rehearsed for this release.
No Mac installer was built on this Windows computer.

See `../cloudflare/smartstock-installer-portal/README.md` for the build-and-publish
workflow. Later releases automatically change each platform's private latest
selection only after downloading and verifying the uploaded installer.
