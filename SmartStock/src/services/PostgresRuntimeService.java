package services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class PostgresRuntimeService {
    private PostgresRuntimeService() {
    }

    public static CommandResult installOrUpdateRuntime() throws Exception {
        String script = """
                set -e
                find_pg_formula() {
                  brew services list 2>/dev/null | awk '/^postgresql(@[0-9]+)?[[:space:]]/ {print $1}' | sort -Vr | head -n 1
                }
                start_pg() {
                  formula="$(find_pg_formula)"
                  if [ -z "$formula" ]; then
                    formula="postgresql"
                  fi
                  brew services start "$formula" || brew services restart "$formula" || true
                }
                if ! command -v brew >/dev/null 2>&1; then
                  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
                  if [ -x /opt/homebrew/bin/brew ]; then eval "$(/opt/homebrew/bin/brew shellenv)"; fi
                  if [ -x /usr/local/bin/brew ]; then eval "$(/usr/local/bin/brew shellenv)"; fi
                fi
                if ! command -v java >/dev/null 2>&1; then brew install openjdk@17; fi
                if ! command -v mvn >/dev/null 2>&1; then brew install maven; fi
                if ! command -v psql >/dev/null 2>&1; then brew install postgresql; fi
                start_pg
                psql --version
                brew services list | grep -E 'postgresql|Name' || true
                """;
        return runShell(script, Duration.ofMinutes(20));
    }

    public static CommandResult startPostgres() throws Exception {
        return runShell("""
                set -e
                formula="$(brew services list 2>/dev/null | awk '/^postgresql(@[0-9]+)?[[:space:]]/ {print $1}' | sort -Vr | head -n 1)"
                if [ -z "$formula" ]; then formula="postgresql"; fi
                brew services start "$formula" || brew services restart "$formula" || true
                psql --version
                brew services list | grep -E 'postgresql|Name' || true
                """, Duration.ofMinutes(2));
    }

    public static CommandResult postgresStatus() throws Exception {
        return runShell("command -v psql && psql --version && brew services list | grep -E 'postgresql|Name' || true", Duration.ofSeconds(30));
    }

    public static CommandResult ensureLanServerAccess(int port) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new CommandResult(false,
                    "Automatic PostgreSQL LAN configuration is not implemented for Windows yet. "
                            + "Configure postgresql.conf listen_addresses and pg_hba.conf manually on the server.");
        }
        int cleanPort = port <= 0 ? 5432 : port;
        String script = """
                set -e
                find_pg_formula() {
                  brew services list 2>/dev/null | awk '/^postgresql(@[0-9]+)?[[:space:]]/ {print $1}' | sort -Vr | head -n 1
                }
                formula="$(find_pg_formula)"
                if [ -z "$formula" ]; then formula="postgresql"; fi
                brew services start "$formula" >/dev/null 2>&1 || brew services restart "$formula" >/dev/null 2>&1 || true

                CONFIG_FILE="$(psql -h 127.0.0.1 -p %d -d postgres -Atc 'show config_file')"
                HBA_FILE="$(psql -h 127.0.0.1 -p %d -d postgres -Atc 'show hba_file')"
                TS="$(date +%%Y%%m%%d%%H%%M%%S)"
                cp "$CONFIG_FILE" "$CONFIG_FILE.smartstock-lan-backup-$TS"
                cp "$HBA_FILE" "$HBA_FILE.smartstock-lan-backup-$TS"

                if grep -Eq "^[[:space:]]*#?[[:space:]]*listen_addresses[[:space:]]*=" "$CONFIG_FILE"; then
                  perl -0pi -e "s/^[[:space:]]*#?[[:space:]]*listen_addresses[[:space:]]*=.*$/listen_addresses = '*'\\t\\t# SmartStock LAN server/m" "$CONFIG_FILE"
                else
                  printf "\\nlisten_addresses = '*'\\t\\t# SmartStock LAN server\\n" >> "$CONFIG_FILE"
                fi

                if ! grep -Eq "^[[:space:]]*host[[:space:]]+smartstock[[:space:]]+all[[:space:]]+samenet[[:space:]]+scram-sha-256" "$HBA_FILE"; then
                  cat >> "$HBA_FILE" <<'EOF'

# SmartStock LAN clients on directly connected local networks. Password authentication is required.
host    smartstock      all             samenet                 scram-sha-256
EOF
                fi

                brew services restart "$formula"
                psql -h 127.0.0.1 -p %d -d postgres -Atc 'show listen_addresses'
                """.formatted(cleanPort, cleanPort, cleanPort);
        return runShell(script, Duration.ofMinutes(2));
    }

    public static CommandResult installSyncService() throws Exception {
        Path appDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return installWindowsSyncTask(appDir);
        }
        return installMacSyncService(appDir);
    }

    public static CommandResult ensureSyncServiceInstalled() throws Exception {
        CommandResult status = syncServiceStatus();
        if (isSyncServiceInstalled(status.output())) {
            return new CommandResult(true, "SmartStock background sync service is already installed.\n\n" + status.output());
        }
        return installSyncService();
    }

    public static CommandResult syncServiceStatus() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return runShell("""
                    schtasks /Query /TN SmartStockBackgroundSync /FO LIST 2>&1 || exit 0
                    """, Duration.ofSeconds(30));
        }
        return runShell("""
                launchctl print gui/$(id -u)/com.smartstock.sync 2>&1 || true
                tail -n 40 "$HOME/.smartstock/sync-service.log" 2>/dev/null || true
                """, Duration.ofSeconds(30));
    }

    private static boolean isSyncServiceInstalled(String statusOutput) {
        if (statusOutput == null || statusOutput.isBlank()) {
            return false;
        }
        String lower = statusOutput.toLowerCase(Locale.ROOT);
        return lower.contains("com.smartstock.sync")
                || lower.contains("taskname:") && lower.contains("smartstockbackgroundsync")
                || lower.contains("task to run:") && lower.contains("run-smartstock-sync-service");
    }

    public static CommandResult runInstallerRepair(Path installerPath, String mode) throws Exception {
        String script = "set -e\n\"" + installerPath.toAbsolutePath() + "\" " + shellWord(mode);
        return runShell(script, Duration.ofMinutes(20));
    }

    private static CommandResult installMacSyncService(Path appDir) throws Exception {
        String script = """
                set -e
                APP_DIR=%s
                SERVICE_DIR="$HOME/.smartstock/sync-service"
                SERVICE_APP_DIR="$SERVICE_DIR/app"
                mkdir -p "$APP_DIR/target" "$SERVICE_APP_DIR/dependency" "$HOME/.smartstock" "$HOME/Library/LaunchAgents"
                JAR_PATH="$(find "$APP_DIR/target" -maxdepth 1 -name 'inventory-management-*.jar' -type f | sort | tail -n 1)"
                if [ -z "$JAR_PATH" ]; then
                  echo "No SmartStock jar found in $APP_DIR/target. Run mvn package first." >&2
                  exit 1
                fi
                JAR_NAME="$(basename "$JAR_PATH")"
                rm -f "$SERVICE_APP_DIR"/inventory-management-*.jar
                cp "$JAR_PATH" "$SERVICE_APP_DIR/"
                if [ -d "$APP_DIR/target/dependency" ]; then
                  rm -rf "$SERVICE_APP_DIR/dependency"
                  cp -R "$APP_DIR/target/dependency" "$SERVICE_APP_DIR/dependency"
                fi
                cat > "$SERVICE_DIR/run-smartstock-sync-service.command" <<EOF
                #!/usr/bin/env bash
                cd "$SERVICE_APP_DIR"
                exec java -jar "$JAR_NAME" --sync-service
                EOF
                chmod +x "$SERVICE_DIR/run-smartstock-sync-service.command"
                PLIST="$HOME/Library/LaunchAgents/com.smartstock.sync.plist"
                cat > "$PLIST" <<EOF
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                  <key>Label</key>
                  <string>com.smartstock.sync</string>
                  <key>ProgramArguments</key>
                  <array>
                    <string>$SERVICE_DIR/run-smartstock-sync-service.command</string>
                  </array>
                  <key>RunAtLoad</key>
                  <true/>
                  <key>KeepAlive</key>
                  <true/>
                  <key>StandardOutPath</key>
                  <string>$HOME/.smartstock/sync-service.log</string>
                  <key>StandardErrorPath</key>
                  <string>$HOME/.smartstock/sync-service.err.log</string>
                  <key>WorkingDirectory</key>
                  <string>$SERVICE_APP_DIR</string>
                </dict>
                </plist>
                EOF
                launchctl bootout "gui/$(id -u)" "$PLIST" >/dev/null 2>&1 || true
                launchctl bootstrap "gui/$(id -u)" "$PLIST"
                launchctl kickstart -k "gui/$(id -u)/com.smartstock.sync"
                printf 'Installed SmartStock background sync LaunchAgent: %%s\\n' "$PLIST"
                """.formatted(shellSingleQuoted(appDir.toString()));
        return runShell(script, Duration.ofMinutes(2));
    }

    private static CommandResult installWindowsSyncTask(Path appDir) throws Exception {
        String script = """
                $ErrorActionPreference = 'Stop'
                $AppDir = %s
                $ServiceDir = Join-Path $env:USERPROFILE '.smartstock\\sync-service'
                $ServiceAppDir = Join-Path $ServiceDir 'app'
                New-Item -ItemType Directory -Force -Path $ServiceAppDir | Out-Null
                $Jar = Get-ChildItem -Path (Join-Path $AppDir 'target') -Filter 'inventory-management-*.jar' |
                  Sort-Object LastWriteTime -Descending |
                  Select-Object -First 1
                if ($null -eq $Jar) {
                  throw "No SmartStock jar found in $AppDir\\target. Run mvn package first."
                }
                Remove-Item -Force (Join-Path $ServiceAppDir 'inventory-management-*.jar') -ErrorAction SilentlyContinue
                Copy-Item -Force $Jar.FullName $ServiceAppDir
                $SourceDependency = Join-Path $AppDir 'target\\dependency'
                $TargetDependency = Join-Path $ServiceAppDir 'dependency'
                if (Test-Path $SourceDependency) {
                  Remove-Item -Recurse -Force $TargetDependency -ErrorAction SilentlyContinue
                  Copy-Item -Recurse -Force $SourceDependency $TargetDependency
                }
                $Cmd = Join-Path $ServiceDir 'run-smartstock-sync-service.cmd'
                $JarName = Split-Path -Leaf $Jar.FullName
                Set-Content -Path $Cmd -Encoding ASCII -Value @(
                  '@echo off',
                  'cd /d "' + $ServiceAppDir + '"',
                  'java -jar ' + $JarName + ' --sync-service'
                )
                schtasks /Create /TN SmartStockBackgroundSync /TR "`"$Cmd`"" /SC ONSTART /F
                schtasks /Run /TN SmartStockBackgroundSync
                schtasks /Query /TN SmartStockBackgroundSync /FO LIST
                """.formatted(powerShellSingleQuoted(appDir.toString()));
        return runPowerShell(script, Duration.ofMinutes(2));
    }

    private static CommandResult runShell(String script, Duration timeout) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-lc", script);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        boolean exited = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
            return new CommandResult(false, "Command timed out.\n" + output);
        }
        return new CommandResult(process.exitValue() == 0, output.toString().trim());
    }

    private static CommandResult runPowerShell(String script, Duration timeout) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        boolean exited = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
            return new CommandResult(false, "Command timed out.\n" + output);
        }
        return new CommandResult(process.exitValue() == 0, output.toString().trim());
    }

    public record CommandResult(boolean success, String output) {
    }

    private static String shellWord(String value) {
        if (value == null || value.isBlank()) {
            return "server";
        }
        return value.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private static String shellSingleQuoted(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static String powerShellSingleQuoted(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
