# SmartStock macOS Installer

Run this on a new store server Mac, or rerun it later to repair/upgrade an existing install:

```bash
SmartStock/installer/macos/install.command server
```

The installer will:

- install Homebrew if needed;
- install Java 17, Maven, and PostgreSQL when missing;
- start PostgreSQL;
- create the local `smartstock` database and `smartstock_server` role;
- bind PostgreSQL to loopback and disable any legacy register database roles;
- install the HTTPS SmartStock Server Service on port `8443`;
- create the local pairing, device-session, approval, idempotency, and immutable audit schema;
- never create a register PostgreSQL role or distribute database credentials;
- keep privileged database credentials in macOS Keychain;
- repair old installs that accidentally saved placeholders such as `SMARTSTOCK_DB_USER`;
- back up existing config/credential files before rewriting them;
- apply SmartStock local sync and employee credential tables;
- write `~/.smartstock/database.properties`;
- build the Java app;
- fail installation if the zero-client-JDBC or repository security checks fail;
- create server/client launchers under `SmartStock/target/`.

For customer-facing Mac distribution, build a `.app` update payload and optional
DMG with:

```bash
bash SmartStock/tools/package-macos-release.sh
```

Upload the generated zip to the `smartstock-releases` bucket using the
`mac/...` path printed by the script, then publish the printed `app_releases`
metadata row with `platform = 'mac'`.

## Sign And Notarize

For public Mac distribution, sign and notarize the app and DMG after packaging:

```bash
xcrun notarytool store-credentials SmartStockNotary \
  --apple-id you@example.com \
  --team-id YOURTEAMID \
  --password APP-SPECIFIC-PASSWORD

DEVELOPER_ID_APPLICATION="Developer ID Application: Your Name (TEAMID)" \
bash SmartStock/tools/notarize-macos-release.sh
```

The notarization script staples tickets to both `SmartStock.app` and the DMG,
then prints the new zip checksum to use in `app_releases`.

Optional environment overrides:

```bash
SMARTSTOCK_DB_NAME=smartstock \
SMARTSTOCK_DB_USER=smartstock_server \
SMARTSTOCK_DB_PASSWORD='change-me' \
SMARTSTOCK_DB_PORT=5432 \
SmartStock/installer/macos/install.command server
```

After installation, open `SmartStock/target/run-smartstock-server.command`.

Credentials are saved here:

```text
~/.smartstock/database-credentials.txt
```

The server database identity is stored in macOS Keychain and used only on the
physical server. The credentials note records the server username and loopback
URL, but never the password.
Register/client machines use one-time administrator pairing and keep only their
revocable device API credential in macOS Keychain. Employees continue to log in
normally and are not shown pairing prompts during daily use.

For a register-only installation, run:

```bash
SmartStock/installer/macos/install.command client
```

Client installation does not install PostgreSQL or retain database/cloud
credentials. Pairing automatically discovers the server by the administrator's
temporary verification phrase; `SMARTSTOCK_LAN_SERVER_HOST` is an optional
manual hostname fallback.

## Repair Existing Install

Rerun the installer without reset:

```bash
SmartStock/installer/macos/install.command server
```

This keeps the local database and repairs roles, passwords, grants, config files, schema, and launchers.

## Reset Local Database

Only use this if you intentionally want to drop and recreate the local SmartStock database:

```bash
SMARTSTOCK_CONFIRM_RESET=YES SmartStock/installer/macos/install.command server --reset-local-db
```

Normal repair mode does not delete the local database.
