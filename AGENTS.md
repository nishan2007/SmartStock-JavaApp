# SmartStock Agent Guide

This repository contains the SmartStock Java/Swing desktop application and its
local-first store server tooling. These instructions apply to the entire
repository on macOS and Windows.

## Repository layout

- `SmartStock/src`: application source and packaged resources
- `SmartStock/test`: JUnit tests
- `SmartStock/database`: base schema and ordered migrations
- `SmartStock/tools`: validation, packaging, deployment, and maintenance tools
- `SmartStock/installer/windows`: Windows server and service installers
- `SmartStock/installer/macos`: macOS installer resources

Run Maven commands from `SmartStock`, or use `mvn -f SmartStock/pom.xml` from
the repository root. The project requires Java 17 or later.

## Required working practices

- Inspect `git status` before editing. Preserve unrelated user changes and do
  not stage, discard, or rewrite them unless explicitly requested.
- Use `rg` or `rg --files` for repository searches.
- Keep the store local-first. PostgreSQL and the authenticated HTTPS LAN API are
  the live store authority. Supabase is limited to Auth, Storage, synchronization,
  updates, and off-site recovery; it is not the register-facing POS database.
- Never commit credentials, tokens, pairing codes, private keys, database dumps,
  production identity manifests, or recovery evidence.
- Registers must not receive PostgreSQL or Supabase server credentials.
- Schema changes must cover every applicable schema and installer path: base
  SQL, migrations, local/server provisioning, LAN reference sync, and prepared
  statement indexes.
- Security and administrator operations require server-side enforcement. UI-only
  permission checks are not sufficient.
- Preserve existing Swing workflows, responsive behavior, and the shared
  Deckers palette/components instead of introducing one-off styling.
- Treat physical printers, cash drawers, NFC readers, Windows services, installed
  applications, and live databases as separate verification steps. Automated
  tests alone do not prove deployed hardware behavior.
- Do not publish releases or mutate release metadata until the exact artifact's
  download, size, and SHA-256 have been verified.

## Validation

Use validation proportional to the change. The normal full gate from the
repository root is:

```sh
mvn -q -f SmartStock/pom.xml test
SmartStock/tools/security-check.sh
git diff --check
```

On Windows PowerShell, run tests with:

```powershell
mvn -q -f SmartStock/pom.xml test
git diff --check
```

The security check is a Bash script. Run it through Git Bash or WSL on Windows:

```bash
SmartStock/tools/security-check.sh
```

For packaging changes, use the platform's existing packaging script and verify
the resulting installed application, not only the build output:

- Windows: `SmartStock/tools/package-windows-release.ps1`
- macOS: `SmartStock/tools/package-macos-release.sh`

Report which checks were run and identify any live Windows, database, service,
printer, drawer, NFC, or installed-app checks that remain outstanding.

## Cross-computer Git workflow

- Keep a separate clone on each computer; do not sync the live working tree with
  iCloud, OneDrive, Dropbox, or another file-sync service.
- Before moving to the other computer, finish or checkpoint the current work on
  a branch, commit it intentionally, and push it. Pull that branch on the other
  computer before editing.
- When macOS and Windows work continue concurrently, use separate branches and
  merge only after each platform's relevant checks pass.
- Do not commit machine-local configuration merely to transfer it. Document the
  required variable names and configure their secret values separately.
