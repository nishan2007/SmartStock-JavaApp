# SmartStock Java App

SmartStock is a Java 17/Swing inventory and point-of-sale application with a
local-first PostgreSQL store server, an authenticated HTTPS LAN API for
registers, and isolated cloud services for authentication, storage,
synchronization, updates, and off-site recovery.

## Requirements

- Git
- JDK 17 or later
- Maven 3.9 or later
- Git Bash or WSL on Windows for the Bash-based security check
- PostgreSQL only when configuring a SmartStock store server

Confirm the core tools after installation:

```text
git --version
java -version
mvn -version
```

## Clone and test

Clone the private repository separately on each computer. Do not place the live
Git working directory inside iCloud, OneDrive, Dropbox, or a similar sync folder.

```sh
git clone https://github.com/nishan2007/SmartStock-JavaApp.git
cd SmartStock-JavaApp
mvn -q -f SmartStock/pom.xml test
```

Run the remaining repository checks from macOS, Linux, Git Bash, or WSL:

```sh
SmartStock/tools/security-check.sh
git diff --check
```

## Run the application for development

From the repository root:

```sh
mvn -q -f SmartStock/pom.xml exec:java -Dexec.mainClass=app.Main
```

Local database credentials and machine-specific configuration must be supplied
outside Git. Never commit passwords, tokens, private keys, pairing codes, or
production recovery artifacts.

## Work between macOS and Windows

GitHub transfers source changes; installing Codex or ChatGPT on both computers
does not automatically transfer uncommitted files or machine-local settings.

Before switching computers:

```sh
git status
git add <the-files-you-intend-to-share>
git commit -m "Checkpoint current work"
git push
```

On the other computer:

```sh
git pull --ff-only
```

Use separate branches when both computers are changing the project at the same
time. Keep Windows-only validation on a Windows branch until it has passed, then
merge it with the main development work.

## Platform packaging

- Current source release version: `1.0.41`
- Version source of truth: `SmartStock/pom.xml`
- Windows: `SmartStock/tools/package-windows-release.ps1`
- macOS: `SmartStock/tools/package-macos-release.sh`

Both packaging scripts read the Maven project version and use it in the JAR,
application image, and release artifact names. Before packaging either platform,
update the single `<version>` value in `SmartStock/pom.xml`, commit that version
change with the release work, and build both platforms from the same commit.
Do not maintain separate Windows and macOS version numbers.

Confirm the shared version from the repository root with:

```sh
mvn -q -f SmartStock/pom.xml help:evaluate \
  -Dexpression=project.version -DforceStdout
```

Packaging success does not prove an installed release. Verify the actual
installed app, service health, database route, and relevant hardware on the
target operating system before treating a release as complete.

Repository-wide instructions for Codex and other coding agents are in
[`AGENTS.md`](AGENTS.md).
