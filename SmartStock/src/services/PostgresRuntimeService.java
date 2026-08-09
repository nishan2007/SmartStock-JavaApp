package services;

import data.DatabaseConfig;
import data.EnvironmentProfile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class PostgresRuntimeService {
    private PostgresRuntimeService() {
    }

    public static CommandResult installOrUpdateRuntime() throws Exception {
        if (isWindows()) {
            return installWindowsPostgres();
        }
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
                if ! command -v psql >/dev/null 2>&1; then brew install postgresql; fi
                start_pg
                psql --version
                brew services list | grep -E 'postgresql|Name' || true
                """;
        return runShell(script, Duration.ofMinutes(20));
    }

    public static ServerPrerequisites checkServerPrerequisites() {
        int javaVersion = Runtime.version().feature();
        String command = isWindows()
                ? """
                  $psql = Get-Command psql.exe -ErrorAction SilentlyContinue
                  if (-not $psql) {
                    $psql = Get-ChildItem 'C:\\Program Files\\PostgreSQL\\*\\bin\\psql.exe' -ErrorAction SilentlyContinue |
                      Sort-Object FullName -Descending | Select-Object -First 1
                  }
                  if (-not $psql) { exit 1 }
                  $psqlPath = $psql.Source
                  if (-not $psqlPath) { $psqlPath = $psql.FullName }
                  if (-not $psqlPath) { exit 1 }
                  & $psqlPath --version
                  """
                : "command -v psql >/dev/null 2>&1 && psql --version";
        try {
            CommandResult result = isWindows()
                    ? runPowerShell(command, Duration.ofSeconds(20))
                    : runShell(command, Duration.ofSeconds(20));
            int postgresVersion = parsePostgresMajorVersion(result.output());
            return new ServerPrerequisites(
                    javaVersion >= 17,
                    javaVersion,
                    result.success() && postgresVersion >= 15,
                    postgresVersion,
                    result.output());
        } catch (Exception ex) {
            return new ServerPrerequisites(javaVersion >= 17, javaVersion,
                    false, 0, ex.getMessage() == null ? "" : ex.getMessage());
        }
    }

    static int parsePostgresMajorVersion(String output) {
        if (output == null) return 0;
        var matcher = java.util.regex.Pattern
                .compile("(?i)(?:postgresql|psql)[^0-9]*([0-9]{1,2})(?:\\.[0-9]+)?")
                .matcher(output);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static CommandResult installWindowsPostgres() throws Exception {
        Path setupDir = Path.of(System.getProperty("user.home"), ".smartstock", "setup");
        Files.createDirectories(setupDir);
        utils.SecureFilePermissions.restrictDirectoryToOwner(setupDir);
        Path scriptPath = Files.createTempFile(setupDir, "install-postgresql-", ".ps1");
        Path logPath = Files.createTempFile(setupDir, "install-postgresql-", ".log");
        Path optionPath = Files.createTempFile(setupDir, "postgresql-installer-", ".conf");
        Path bootstrapCredentialPath = LocalDatabaseBootstrapService.windowsBootstrapCredentialPath();
        utils.SecureFilePermissions.restrictFileToOwner(optionPath);
        String script = """
                $ErrorActionPreference = 'Stop'
                $Log = %s
                $OptionFile = %s
                $BootstrapCredential = %s
                try {
                  $Existing = Get-ChildItem 'C:\\Program Files\\PostgreSQL\\*\\bin\\psql.exe' -ErrorAction SilentlyContinue |
                    Sort-Object FullName -Descending | Select-Object -First 1
                  $WasInstalled = [bool]$Existing
                  $PasswordBytes = New-Object byte[] 32
                  $Rng = [Security.Cryptography.RandomNumberGenerator]::Create()
                  try { $Rng.GetBytes($PasswordBytes) } finally { $Rng.Dispose() }
                  $SuperPassword = [Convert]::ToBase64String($PasswordBytes).TrimEnd('=').Replace('+','-').Replace('/','_')
                  [Array]::Clear($PasswordBytes, 0, $PasswordBytes.Length)
                  if (-not $Existing) {
                    Set-Content -LiteralPath $OptionFile -Encoding ASCII -Value @(
                      'mode=unattended',
                      'unattendedmodeui=minimal',
                      'superpassword=' + $SuperPassword
                    )
                    $BundledInstaller = Join-Path (Split-Path -Parent $PSScriptRoot) 'postgresql-installer.exe'
                    if (Test-Path $BundledInstaller) {
                      $Install = Start-Process -FilePath $BundledInstaller `
                        -ArgumentList @('--optionfile', $OptionFile) -Wait -PassThru
                      if ($Install.ExitCode -ne 0) { throw "PostgreSQL installer exited with code $($Install.ExitCode)." }
                    } elseif (Get-Command winget.exe -ErrorAction SilentlyContinue) {
                      Write-Host 'Starting PostgreSQL installation. This normally takes 5-15 minutes.'
                      Write-Host 'SmartStock will print a progress message every 15 seconds; leave this window open.'
                      $Arguments = @(
                        'install', '--id', 'PostgreSQL.PostgreSQL.17', '--exact', '--silent',
                        '--override', ('--optionfile "{0}"' -f $OptionFile),
                        '--accept-package-agreements', '--accept-source-agreements'
                      )
                      $Install = Start-Process -FilePath 'winget.exe' -ArgumentList $Arguments `
                        -PassThru -NoNewWindow
                      $Started = Get-Date
                      while (-not $Install.HasExited) {
                        $Elapsed = (Get-Date) - $Started
                        if ($Elapsed.TotalMinutes -ge 20) {
                          Stop-Process -Id $Install.Id -Force -ErrorAction SilentlyContinue
                          throw 'PostgreSQL installation exceeded 20 minutes. Review the visible installer window or setup log, then retry.'
                        }
                        Write-Host ("PostgreSQL installation is still running ({0:mm\\:ss} elapsed)..." -f $Elapsed)
                        Start-Sleep -Seconds 15
                        $Install.Refresh()
                      }
                      if ($Install.ExitCode -ne 0) {
                        throw "Windows Package Manager could not install PostgreSQL (exit code $($Install.ExitCode))."
                      }
                      Write-Host 'PostgreSQL package installation completed; verifying its service and tools.'
                    } else {
                      throw "PostgreSQL is missing and Windows Package Manager is unavailable. Use a SmartStock installer bundle that includes postgresql-installer.exe."
                    }
                    Remove-Item -LiteralPath $OptionFile -Force -ErrorAction SilentlyContinue
                  }
                  $Services = Get-Service 'postgresql*' -ErrorAction SilentlyContinue
                  if (-not $Services) { throw 'PostgreSQL installed, but its Windows service was not found.' }
                  $Services | Set-Service -StartupType Automatic
                  $Services | Start-Service
                  $Psql = Get-ChildItem 'C:\\Program Files\\PostgreSQL\\*\\bin\\psql.exe' -ErrorAction SilentlyContinue |
                    Sort-Object FullName -Descending | Select-Object -First 1
                  if (-not $Psql) { throw 'PostgreSQL command-line tools were not found after installation.' }
                  if ($WasInstalled) {
                    $Hba = Get-ChildItem 'C:\\Program Files\\PostgreSQL\\*\\data\\pg_hba.conf' -ErrorAction SilentlyContinue |
                      Sort-Object FullName -Descending | Select-Object -First 1
                    if (-not $Hba) { throw 'PostgreSQL pg_hba.conf was not found for secure password repair.' }
                    $OriginalHba = [IO.File]::ReadAllBytes($Hba.FullName)
                    try {
                      $Utf8 = [Text.UTF8Encoding]::new($false)
                      $OriginalText = $Utf8.GetString($OriginalHba)
                      [IO.File]::WriteAllText($Hba.FullName,
                        "host postgres postgres 127.0.0.1/32 trust`r`n" + $OriginalText, $Utf8)
                      $Services | Restart-Service -Force
                      $SqlPassword = $SuperPassword.Replace("'", "''")
                      "ALTER ROLE postgres WITH PASSWORD '$SqlPassword';" |
                        & $Psql.FullName -h 127.0.0.1 -U postgres -d postgres -v ON_ERROR_STOP=1
                      if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL administrator password repair failed.' }
                    } finally {
                      [IO.File]::WriteAllBytes($Hba.FullName, $OriginalHba)
                      $Services | Restart-Service -Force
                    }
                  }
                  Add-Type -AssemblyName System.Security
                  $CredentialBytes = [Text.Encoding]::UTF8.GetBytes($SuperPassword)
                  try {
                    $Protected = [Security.Cryptography.ProtectedData]::Protect(
                      $CredentialBytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
                    if ($null -eq $Protected) { throw 'Windows DPAPI returned no PostgreSQL bootstrap credential.' }
                    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $BootstrapCredential) | Out-Null
                    Set-Content -LiteralPath $BootstrapCredential -Encoding ASCII `
                      -Value ([Convert]::ToBase64String($Protected))
                  } finally {
                    [Array]::Clear($CredentialBytes, 0, $CredentialBytes.Length)
                  }
                  $SuperPassword = $null
                  (& $Psql.FullName --version) | Set-Content -LiteralPath $Log
                  Add-Content -LiteralPath $Log -Value 'PostgreSQL service is installed and set to start automatically.'
                  exit 0
                } catch {
                  Remove-Item -LiteralPath $OptionFile -Force -ErrorAction SilentlyContinue
                  $_ | Out-String | Set-Content -LiteralPath $Log
                  exit 1
                }
                """.formatted(
                powerShellSingleQuoted(logPath.toString()),
                powerShellSingleQuoted(optionPath.toString()),
                powerShellSingleQuoted(bootstrapCredentialPath.toString()));
        Files.writeString(scriptPath, script, StandardCharsets.UTF_8);
        utils.SecureFilePermissions.restrictFileToOwner(scriptPath);
        utils.SecureFilePermissions.restrictFileToOwner(logPath);
        String elevate = "$ErrorActionPreference='Stop';"
                + "$p=Start-Process -FilePath 'powershell.exe' -Verb RunAs -Wait -PassThru "
                + "-ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',"
                + powerShellSingleQuoted(scriptPath.toString()) + ");"
                + "exit $p.ExitCode";
        try {
            CommandResult elevated = runPowerShell(elevate, Duration.ofMinutes(20));
            String log = Files.isRegularFile(logPath)
                    ? Files.readString(logPath, StandardCharsets.UTF_8).trim() : "";
            return new CommandResult(elevated.success(),
                    log.isBlank() ? elevated.output() : log);
        } finally {
            Files.deleteIfExists(scriptPath);
            Files.deleteIfExists(logPath);
            Files.deleteIfExists(optionPath);
            if (Files.isRegularFile(bootstrapCredentialPath)) {
                utils.SecureFilePermissions.restrictFileToOwner(bootstrapCredentialPath);
            }
        }
    }

    public static CommandResult startPostgres() throws Exception {
        if (isWindows()) {
            return runPowerShell("$ErrorActionPreference='Stop'; $services=Get-Service 'postgresql*' -ErrorAction SilentlyContinue; if(-not $services){throw 'PostgreSQL Windows service was not found.'}; $services | Start-Service; $services | Format-Table Name,Status -AutoSize", Duration.ofMinutes(2));
        }
        return runShell("""
                set -e
                formula="$(brew services list 2>/dev/null | awk '/^postgresql(@[0-9]+)?[[:space:]]/ {print $1}' | sort -Vr | head -n 1)"
                if [ -z "$formula" ]; then formula="postgresql"; fi
                brew services start "$formula" || brew services restart "$formula" || true
                psql --version
                brew services list | grep -E 'postgresql|Name' || true
                """, Duration.ofMinutes(2));
    }

    public static CommandResult stopPostgres() throws Exception {
        if (isWindows()) {
            return runPowerShell("$ErrorActionPreference='Stop'; $services=Get-Service 'postgresql*' -ErrorAction SilentlyContinue; if(-not $services){throw 'PostgreSQL Windows service was not found.'}; $services | Stop-Service -Force; $services | Format-Table Name,Status -AutoSize", Duration.ofMinutes(2));
        }
        return runShell("""
                set -e
                formula="$(brew services list 2>/dev/null | awk '/^postgresql(@[0-9]+)?[[:space:]]/ {print $1}' | sort -Vr | head -n 1)"
                if [ -z "$formula" ]; then echo 'PostgreSQL Homebrew service was not found.' >&2; exit 1; fi
                brew services stop "$formula"
                brew services list | grep -E 'postgresql|Name' || true
                """, Duration.ofMinutes(2));
    }

    public static CommandResult postgresStatus() throws Exception {
        if (isWindows()) {
            return runPowerShell("$services=Get-Service 'postgresql*' -ErrorAction SilentlyContinue; if(-not $services){Write-Output 'PostgreSQL Windows service was not found.'; exit 1}; $services | Format-Table Name,Status -AutoSize", Duration.ofSeconds(30));
        }
        return runShell("command -v psql && psql --version && brew services list | grep -E 'postgresql|Name' || true", Duration.ofSeconds(30));
    }

    public static CommandResult startLanService() throws Exception {
        ensureSyncServiceInstalled();
        if (isWindows()) {
            return runPowerShell("""
                    $ErrorActionPreference = 'Stop'
                    $task = Get-ScheduledTask -TaskName SmartStockServerService -ErrorAction Stop
                    Start-ScheduledTask -InputObject $task -ErrorAction Stop
                    Start-Sleep -Milliseconds 500
                    Get-ScheduledTask -TaskName SmartStockServerService | Format-List TaskName,State
                    """, Duration.ofSeconds(30));
        }
        return runShell("""
                set -e
                plist="$HOME/Library/LaunchAgents/com.smartstock.sync.plist"
                if [ ! -f "$plist" ]; then echo 'SmartStock LAN service is not installed.' >&2; exit 1; fi
                launchctl bootstrap "gui/$(id -u)" "$plist" >/dev/null 2>&1 || true
                launchctl kickstart -k "gui/$(id -u)/com.smartstock.sync"
                launchctl print "gui/$(id -u)/com.smartstock.sync" | grep -E 'state =|pid =|program ='
                """, Duration.ofSeconds(30));
    }

    public static CommandResult startServerProcesses() throws Exception {
        if (isWindows()) {
            return installWindowsServer(SupabaseProjectConfig.load(),
                    EnvironmentProfile.active(), SupabaseProjectConfig.loadLanSubnet());
        }
        CommandResult database = startPostgres();
        if (!database.success()) return database;
        CommandResult lan = startLanService();
        return new CommandResult(lan.success(), database.output() + "\n" + lan.output());
    }

    public static CommandResult stopLanService() throws Exception {
        if (isWindows()) {
            return runPowerShell("""
                    $ErrorActionPreference = 'Stop'
                    $task = Get-ScheduledTask -TaskName SmartStockServerService -ErrorAction Stop
                    Stop-ScheduledTask -InputObject $task -ErrorAction SilentlyContinue
                    Get-ScheduledTask -TaskName SmartStockServerService | Format-List TaskName,State
                    """, Duration.ofSeconds(30));
        }
        return runShell("""
                plist="$HOME/Library/LaunchAgents/com.smartstock.sync.plist"
                launchctl bootout "gui/$(id -u)" "$plist" >/dev/null 2>&1 || true
                if launchctl print "gui/$(id -u)/com.smartstock.sync" >/dev/null 2>&1; then
                  echo 'SmartStock LAN service is still running.' >&2
                  exit 1
                fi
                echo 'SmartStock LAN service stopped.'
                """, Duration.ofSeconds(30));
    }

    public static CommandResult ensureServiceOnlyDatabaseAccess(DatabaseConfig config) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String script = """
                    $ErrorActionPreference = 'Stop'
                    $env:PGPASSWORD = $env:SMARTSTOCK_RUNTIME_DB_PASSWORD
                    $Port = %d
                    $User = $env:SMARTSTOCK_RUNTIME_DB_USER
                    $HbaFile = (& psql -h 127.0.0.1 -p $Port -U $User -d postgres -Atc 'show hba_file').Trim()
                    if ($LASTEXITCODE -ne 0 -or -not $HbaFile) { throw 'Could not locate pg_hba.conf.' }
                    $Stamp = Get-Date -Format 'yyyyMMddHHmmss'
                    Copy-Item -LiteralPath $HbaFile -Destination ($HbaFile + '.smartstock-service-only-backup-' + $Stamp)
                    $Lines = Get-Content -LiteralPath $HbaFile
                    $Lines = $Lines | ForEach-Object {
                      if ($_ -match '^\\s*host(?:ssl|nossl)?\\s+smartstock\\s+\\S+\\s+samenet\\s+' -or
                          $_ -match '^\\s*host(?:ssl|nossl)?\\s+\\S+\\s+smartstock_(?:client|device_)') {
                        '# disabled by SmartStock HTTPS single cutover: ' + $_
                      } else { $_ }
                    }
                    Set-Content -LiteralPath $HbaFile -Value $Lines -Encoding ASCII
                    & psql -h 127.0.0.1 -p $Port -U $User -d postgres -v ON_ERROR_STOP=1 -c "ALTER SYSTEM SET listen_addresses = 'localhost';"
                    if ($LASTEXITCODE -ne 0) { throw 'Could not bind PostgreSQL to localhost.' }
                    $Services = Get-Service 'postgresql*' -ErrorAction SilentlyContinue
                    if (-not $Services) { throw 'PostgreSQL Windows service was not found.' }
                    $Services | Restart-Service -Force
                    Start-Sleep -Seconds 2
                    $Listen = (& psql -h 127.0.0.1 -p $Port -U $User -d postgres -Atc 'show listen_addresses').Trim()
                    if ($Listen -ne 'localhost') { throw "PostgreSQL is still listening on: $Listen" }
                    Write-Output 'PostgreSQL is bound to localhost; registers use HTTPS port 8443.'
                    """.formatted(config.serverPort() <= 0 ? 5432 : config.serverPort());
            return runPowerShell(script, Duration.ofMinutes(2), Map.of(
                    "SMARTSTOCK_RUNTIME_DB_USER", config.dbUser(),
                    "SMARTSTOCK_RUNTIME_DB_PASSWORD", config.dbPassword()
            ));
        }
        int cleanPort = config.serverPort() <= 0 ? 5432 : config.serverPort();
        String script = """
                set -e
                find_pg_formula() {
                  brew services list 2>/dev/null | awk '/^postgresql(@[0-9]+)?[[:space:]]/ {print $1}' | sort -Vr | head -n 1
                }
                formula="$(find_pg_formula)"
                if [ -z "$formula" ]; then formula="postgresql"; fi
                brew services start "$formula" >/dev/null 2>&1 || brew services restart "$formula" >/dev/null 2>&1 || true

                if psql -d postgres -Atc 'select 1' >/dev/null 2>&1; then
                  PSQL_ADMIN=(psql -d postgres)
                else
                  PSQL_ADMIN=(psql -h 127.0.0.1 -p %d -U "$SMARTSTOCK_RUNTIME_DB_USER" -d postgres)
                fi
                CONFIG_FILE="$("${PSQL_ADMIN[@]}" -Atc 'show config_file')"
                HBA_FILE="$("${PSQL_ADMIN[@]}" -Atc 'show hba_file')"
                DATA_DIR="$("${PSQL_ADMIN[@]}" -Atc 'show data_directory')"
                TS="$(date +%%Y%%m%%d%%H%%M%%S)"
                cp "$CONFIG_FILE" "$CONFIG_FILE.smartstock-service-only-backup-$TS"
                cp "$HBA_FILE" "$HBA_FILE.smartstock-service-only-backup-$TS"

                if grep -Eq "^[[:space:]]*#?[[:space:]]*listen_addresses[[:space:]]*=" "$CONFIG_FILE"; then
                  perl -0pi -e "s/^[[:space:]]*#?[[:space:]]*listen_addresses[[:space:]]*=.*$/listen_addresses = 'localhost'\\t\\t# SmartStock Server Service only/m" "$CONFIG_FILE"
                else
                  printf "\\nlisten_addresses = 'localhost'\\t\\t# SmartStock Server Service only\\n" >> "$CONFIG_FILE"
                fi

                CERT_FILE="$DATA_DIR/smartstock-server.crt"
                KEY_FILE="$DATA_DIR/smartstock-server.key"
                if [ ! -s "$CERT_FILE" ] || [ ! -s "$KEY_FILE" ]; then
                  umask 077
                  openssl req -new -x509 -nodes -newkey rsa:3072 -days 825 -subj "/CN=SmartStock LAN Server" -keyout "$KEY_FILE" -out "$CERT_FILE"
                  chmod 600 "$KEY_FILE"
                  chmod 644 "$CERT_FILE"
                fi
                if grep -Eq "^[[:space:]]*#?[[:space:]]*ssl[[:space:]]*=" "$CONFIG_FILE"; then
                  perl -0pi -e "s/^[[:space:]]*#?[[:space:]]*ssl[[:space:]]*=.*$/ssl = on\\t\\t# SmartStock encrypted LAN/m" "$CONFIG_FILE"
                else
                  printf "\\nssl = on\\t\\t# SmartStock encrypted LAN\\n" >> "$CONFIG_FILE"
                fi
                printf "\\nssl_cert_file = 'smartstock-server.crt'\\nssl_key_file = 'smartstock-server.key'\\n" >> "$CONFIG_FILE"

                perl -0pi -e "s/^([[:space:]]*host(?:ssl|nossl)?[[:space:]]+smartstock[[:space:]]+[^[:space:]]+[[:space:]]+samenet[[:space:]]+.*)$/# disabled by SmartStock HTTPS single cutover: $1/mg" "$HBA_FILE"
                perl -0pi -e "s/^([[:space:]]*host(?:ssl|nossl)?[[:space:]]+[^[:space:]]+[[:space:]]+smartstock_(?:client|device_)[^[:space:]]*[[:space:]]+.*)$/# disabled by SmartStock HTTPS single cutover: $1/mg" "$HBA_FILE"

                brew services restart "$formula"
                for attempt in 1 2 3 4 5 6 7 8 9 10; do
                  if pg_isready -q; then break; fi
                  sleep 1
                done
                "${PSQL_ADMIN[@]}" -Atc 'show listen_addresses; show ssl'
                """.formatted(cleanPort);
        return runShell(script, Duration.ofMinutes(2), Map.of(
                "SMARTSTOCK_RUNTIME_DB_USER", config.dbUser(),
                "PGPASSWORD", config.dbPassword()
        ));
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
            if(isWindows()){
                if(!isWindowsTaskActionHardened())return installWindowsServer(SupabaseProjectConfig.load(),
                        EnvironmentProfile.active(),SupabaseProjectConfig.loadLanSubnet());
                if(!isSyncServiceRunning(status.output()))restartInstalledSyncService();
                return new CommandResult(true,"SmartStock background server is already installed.\n\n"+status.output());
            }
            boolean repairedLauncher = repairInstalledSyncLauncherIfNeeded();
            if (repairedLauncher || isWindows() && !isSyncServiceRunning(status.output())) {
                restartInstalledSyncService();
            }
            return new CommandResult(true, "SmartStock background sync service is already installed.\n\n" + status.output());
        }
        if (isWindows()) {
            return installWindowsServer(SupabaseProjectConfig.load(),
                    EnvironmentProfile.active(), SupabaseProjectConfig.loadLanSubnet());
        }
        return installSyncService();
    }

    /** Replaces the installed service payload and restarts it after an explicit maintenance request. */
    public static CommandResult refreshSyncServiceInstallation() throws Exception {
        return installSyncService();
    }

    private static boolean isWindowsTaskActionHardened() throws Exception {
        CommandResult result=runPowerShell("""
                $task=Get-ScheduledTask -TaskName SmartStockServerService -ErrorAction SilentlyContinue
                if(-not $task){exit 1}
                $action=$task.Actions | Select-Object -First 1
                if($action.Execute -and
                   (($action.Execute -match '(?i)SmartStock\\\\runtime\\\\bin\\\\javaw?\\.exe$' -and $action.Arguments -match '(?i)-jar.+--sync-service') -or
                    ($action.Execute -match '(?i)SmartStock\\\\SmartStock\\.exe$' -and $action.Arguments -match '(?i)(^|\\s)--sync-service($|\\s)'))) { exit 0 }
                exit 1
                """,Duration.ofSeconds(30));
        return result.success();
    }

    /**
     * Performs the Windows-only production service/firewall setup behind a
     * standard UAC prompt. No database password or service-role key is placed
     * in the generated script or command line.
     */
    public static CommandResult installWindowsProductionServer(
            SupabaseProjectConfig project, String lanSubnet) throws Exception {
        return installWindowsServer(project, EnvironmentProfile.PRODUCTION, lanSubnet);
    }

    public static CommandResult installWindowsServer(
            SupabaseProjectConfig project, EnvironmentProfile environment,
            String lanSubnet) throws Exception {
        if (!isWindows()) {
            return new CommandResult(false,
                    "Complete Windows Server Setup is available only on Windows.");
        }
        EnvironmentProfile selected = environment == null
                ? EnvironmentProfile.active() : environment;
        if (project == null || (selected == EnvironmentProfile.PRODUCTION
                && !project.isProduction())) {
            return new CommandResult(false,
                    "Save this environment's Supabase project before installing the Windows service.");
        }
        String cleanSubnet = lanSubnet == null ? "" : lanSubnet.trim();
        if (!validLanSubnet(cleanSubnet)) {
            return new CommandResult(false,
                    "LAN subnet must be LocalSubnet, an IP address, or CIDR such as 192.168.1.0/24.");
        }

        Path jar = currentPackagedJar();
        if (jar == null || !Files.isRegularFile(jar)) {
            return new CommandResult(false,
                    "The packaged SmartStock JAR was not found. Build or install SmartStock first.");
        }
        Path dependencies = jar.getParent().resolve("dependency");
        if (!Files.isDirectory(dependencies)) {
            return new CommandResult(false,
                    "The packaged SmartStock dependency folder was not found beside the JAR.");
        }

        Path setupDir = Path.of(System.getProperty("user.home"), ".smartstock", "setup");
        Files.createDirectories(setupDir);
        utils.SecureFilePermissions.restrictDirectoryToOwner(setupDir);
        Path scriptPath = Files.createTempFile(setupDir, "install-production-server-", ".ps1");
        Path logPath = Files.createTempFile(setupDir, "install-production-server-", ".log");
        Path installRoot=jar.getParent().getParent();
        Path bundledJava = installRoot.resolve("runtime").resolve("bin").resolve("javaw.exe")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(bundledJava)) {
            bundledJava = installRoot.resolve("runtime").resolve("bin").resolve("java.exe")
                    .toAbsolutePath().normalize();
        }
        Path serviceExecutable = Files.isRegularFile(bundledJava) ? bundledJava
                : Path.of(System.getProperty("java.home"), "bin", "java.exe")
                        .toAbsolutePath().normalize();
        boolean useNativeLauncher = false;
        if (!Files.isRegularFile(serviceExecutable)) {
            return new CommandResult(false,
                    "Neither the packaged SmartStock launcher nor a Java service executable was found.");
        }
        String script = windowsProductionInstallScript(
                jar, dependencies, serviceExecutable, useNativeLauncher,
                project.url(), project.publishableKey(), selected.id(), cleanSubnet, logPath);
        Files.writeString(scriptPath, script, StandardCharsets.UTF_8);
        utils.SecureFilePermissions.restrictFileToOwner(scriptPath);
        utils.SecureFilePermissions.restrictFileToOwner(logPath);

        String elevate = "$ErrorActionPreference='Stop';"
                + "$p=Start-Process -FilePath 'powershell.exe' -Verb RunAs -Wait -PassThru "
                + "-ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',"
                + powerShellSingleQuoted(scriptPath.toString()) + ");"
                + "exit $p.ExitCode";
        CommandResult elevated;
        try {
            elevated = runPowerShell(elevate, Duration.ofMinutes(5));
            String log = Files.isRegularFile(logPath)
                    ? Files.readString(logPath, StandardCharsets.UTF_8).trim() : "";
            String output = log.isBlank() ? elevated.output() : log;
            if (!elevated.success() && output.isBlank()) {
                output = "Windows did not run the elevated SmartStock service installer. "
                        + "Approve the administrator prompt and try again.";
            }
            return new CommandResult(elevated.success(), output);
        } finally {
            Files.deleteIfExists(scriptPath);
            Files.deleteIfExists(logPath);
        }
    }

    static String windowsProductionInstallScript(
            Path jar, Path dependencies, Path javaExecutable,
            String supabaseUrl, String publishableKey,
            String lanSubnet, Path logPath) {
        return windowsProductionInstallScript(jar, dependencies, javaExecutable,
                false, supabaseUrl, publishableKey, "production", lanSubnet, logPath);
    }

    static String windowsProductionInstallScript(
            Path jar, Path dependencies, Path javaExecutable,
            String supabaseUrl, String publishableKey, String environment,
            String lanSubnet, Path logPath) {
        return windowsProductionInstallScriptWithArguments(jar, dependencies, javaExecutable,
                "-jar \"" + jar.getFileName() + "\" --sync-service",
                supabaseUrl, publishableKey, environment, lanSubnet, logPath);
    }

    private static String windowsProductionInstallScriptWithArguments(
            Path jar, Path dependencies, Path serviceExecutable, String serviceArguments,
            String supabaseUrl, String publishableKey, String environment,
            String lanSubnet, Path logPath) {
        return """
                $ErrorActionPreference = 'Stop'
                $Log = %s
                try {
                  $Jar = %s
                  $Dependencies = %s
                  $ServiceExecutable = %s
                  $ServiceDir = Join-Path $env:USERPROFILE '.smartstock\\sync-service'
                  $ServiceAppDir = Join-Path $ServiceDir 'app'
                  New-Item -ItemType Directory -Force -Path $ServiceAppDir | Out-Null
                  Remove-Item -Force (Join-Path $ServiceAppDir 'inventory-management-*.jar') -ErrorAction SilentlyContinue
                  Copy-Item -LiteralPath $Jar -Destination $ServiceAppDir -Force
                  $TargetDependencies = Join-Path $ServiceAppDir 'dependency'
                  Remove-Item -Recurse -Force $TargetDependencies -ErrorAction SilentlyContinue
                  Copy-Item -LiteralPath $Dependencies -Destination $TargetDependencies -Recurse -Force
                  $JarName = Split-Path -Leaf $Jar
                  Unregister-ScheduledTask -TaskName SmartStockBackgroundSync `
                    -Confirm:$false -ErrorAction SilentlyContinue
                  $ServiceArguments = %s
                  if ([string]::IsNullOrWhiteSpace($ServiceArguments)) {
                    $TaskAction = New-ScheduledTaskAction -Execute $ServiceExecutable `
                      -WorkingDirectory $ServiceAppDir
                  } else {
                    $TaskAction = New-ScheduledTaskAction -Execute $ServiceExecutable `
                      -Argument $ServiceArguments -WorkingDirectory $ServiceAppDir
                  }
                  $TaskTrigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
                  $TaskPrincipal = New-ScheduledTaskPrincipal `
                    -UserId ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) `
                    -LogonType Interactive -RunLevel Limited
                  Register-ScheduledTask -TaskName SmartStockServerService `
                    -Action $TaskAction -Trigger $TaskTrigger -Principal $TaskPrincipal `
                    -Description 'SmartStock HTTPS LAN and synchronization service' `
                    -Force -ErrorAction Stop | Out-Null
                  $PostgresServices = Get-Service 'postgresql*' -ErrorAction SilentlyContinue
                  if (-not $PostgresServices) { throw 'PostgreSQL Windows service was not found.' }
                  $PostgresServices | Set-Service -StartupType Automatic
                  $PostgresServices | Start-Service
                  Start-ScheduledTask -TaskName SmartStockServerService -ErrorAction Stop
                  $RuleName = 'SmartStock LAN API 8443'
                  Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue |
                    Remove-NetFirewallRule -ErrorAction SilentlyContinue
                  New-NetFirewallRule -DisplayName $RuleName -Direction Inbound -Action Allow `
                    -Protocol TCP -LocalPort 8443 -RemoteAddress %s -Profile Private | Out-Null
                  Get-ScheduledTask -TaskName SmartStockServerService -ErrorAction Stop |
                    Format-List TaskName,State | Out-String | Set-Content -LiteralPath $Log
                  Add-Content -LiteralPath $Log -Value 'Windows service and private-LAN firewall rule installed.'
                  Add-Content -LiteralPath $Log -Value 'PostgreSQL and SmartStock Server Service started.'
                  exit 0
                } catch {
                  $_ | Out-String | Set-Content -LiteralPath $Log
                  exit 1
                }
                """.formatted(
                powerShellSingleQuoted(logPath.toString()),
                powerShellSingleQuoted(jar.toString()),
                powerShellSingleQuoted(dependencies.toString()),
                powerShellSingleQuoted(serviceExecutable.toString()),
                powerShellSingleQuoted(serviceArguments),
                powerShellSingleQuoted(lanSubnet)
        );
    }

    static String windowsProductionInstallScript(
            Path jar, Path dependencies, Path serviceExecutable, boolean nativeLauncher,
            String supabaseUrl, String publishableKey, String environment,
            String lanSubnet, Path logPath) {
        String arguments = nativeLauncher ? (serviceExecutable.getFileName().toString().equalsIgnoreCase("SmartStockServer.exe") ? "" : "--sync-service")
                : "-jar \"" + jar.getFileName() + "\" --sync-service";
        return windowsProductionInstallScriptWithArguments(jar, dependencies,
                serviceExecutable, arguments, supabaseUrl, publishableKey,
                environment, lanSubnet, logPath);
    }

    static boolean validLanSubnet(String value) {
        if (value == null || value.isBlank()) return false;
        if ("LocalSubnet".equalsIgnoreCase(value.trim())) return true;
        return value.trim().matches(
                "(?i)(?:\\d{1,3}\\.){3}\\d{1,3}(?:/(?:[0-9]|[12][0-9]|3[0-2]))?");
    }

    public static boolean validLanSubnetForSetup(String value) {
        return validLanSubnet(value);
    }

    private static boolean repairInstalledSyncLauncherIfNeeded() throws Exception {
        Path currentJar = currentPackagedJar();
        if (currentJar == null) return false;
        Path serviceDir = Path.of(System.getProperty("user.home"), ".smartstock", "sync-service");
        Path serviceAppDir = serviceDir.resolve("app");
        if (!Files.isDirectory(serviceAppDir)) return false;
        Path serviceJar = serviceAppDir.resolve(currentJar.getFileName().toString());
        boolean replacedServiceJar = !Files.exists(serviceJar)
                || Files.mismatch(currentJar, serviceJar) != -1L;
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (replacedServiceJar) {
            if (windows) {
                runPowerShell("$task=Get-ScheduledTask -TaskName SmartStockServerService "
                                + "-ErrorAction SilentlyContinue; if($task){Stop-ScheduledTask "
                                + "-TaskName SmartStockServerService -ErrorAction SilentlyContinue; "
                                + "Start-Sleep -Seconds 1}",
                        Duration.ofSeconds(30));
            }
            Files.copy(currentJar, serviceJar,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Path launcher = serviceDir.resolve(windows
                ? "run-smartstock-sync-service.cmd" : "run-smartstock-sync-service.command");
        String existing = Files.exists(launcher) ? Files.readString(launcher) : "";
        if (windows && existing.contains("SMARTSTOCK_ENVIRONMENT=")) {
            StringBuilder preserved = new StringBuilder("@echo off\r\n");
            existing.lines().filter(line -> line.regionMatches(true, 0, "set \"", 0, 5))
                    .forEach(line -> preserved.append(line).append("\r\n"));
            preserved.append("cd /d \"").append(serviceAppDir).append("\"\r\n")
                    .append(windowsServiceCommand(currentJar, currentJar.getFileName().toString()))
                    .append(" >> \"").append(serviceDir.resolve("sync-service.log"))
                    .append("\" 2>&1\r\n");
            String updated = preserved.toString();
            if (!updated.equals(existing)) Files.writeString(launcher, updated);
            return replacedServiceJar || !updated.equals(existing);
        }
        String expected = installedSyncLauncherContent(windows, serviceAppDir,
                currentJar.getFileName().toString());
        if (expected.equals(existing)) return replacedServiceJar;
        Files.writeString(launcher, expected);
        if (!windows) {
            try {
                Files.setPosixFilePermissions(launcher,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            } catch (UnsupportedOperationException ignored) {
                launcher.toFile().setExecutable(true, true);
            }
        }
        return true;
    }

    private static Path currentPackagedJar() {
        try {
            var source = PostgresRuntimeService.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                Path path = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
                String name = path.getFileName() == null ? "" : path.getFileName().toString();
                if (Files.isRegularFile(path) && name.startsWith("inventory-management-")
                        && name.endsWith(".jar")) return path;
            }
            return findPackagedJar(Path.of(System.getProperty("user.dir"))
                    .toAbsolutePath().normalize());
        } catch (Exception ignored) {
            return null;
        }
    }

    static Path findPackagedJar(Path applicationDirectory) {
        if (applicationDirectory == null) return null;
        for (Path directory : List.of(applicationDirectory.resolve("app"),
                applicationDirectory.resolve("target"), applicationDirectory)) {
            if (!Files.isDirectory(directory)) continue;
            try (var jars = Files.list(directory)) {
                Path found = jars.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString()
                                .startsWith("inventory-management-"))
                        .filter(path -> path.getFileName().toString().endsWith(".jar"))
                        .max(java.util.Comparator.comparingLong(
                                path -> path.toFile().lastModified()))
                        .orElse(null);
                if (found != null) return found.toAbsolutePath().normalize();
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    static String installedSyncLauncherContent(boolean windows, Path appDir, String jarName) {
        if (windows) {
            Path serviceDir = appDir.getParent();
            String serviceDirText;
            if (serviceDir != null) {
                serviceDirText = serviceDir.toString();
            } else {
                String appDirText = appDir.toString().replace('/', '\\');
                int separator = appDirText.lastIndexOf('\\');
                serviceDirText = separator < 0 ? appDirText : appDirText.substring(0, separator);
            }
            return "@echo off\r\ncd /d \"" + appDir + "\"\r\n"
                    + windowsServiceCommand(appDir.resolve(jarName), jarName)
                    + " >> \"" + serviceDirText + "\\sync-service.log"
                    + "\" 2>&1\r\n";
        }
        return "#!/usr/bin/env bash\nset -euo pipefail\n"
                + "cd " + shellSingleQuoted(unixPath(appDir)) + "\n"
                + "exec java -Djava.awt.headless=true -Dapple.awt.UIElement=true -jar "
                + shellSingleQuoted(jarName) + " --sync-service\n";
    }

    private static String windowsServiceCommand(Path packagedJar, String jarName) {
        Path appDirectory = packagedJar.toAbsolutePath().normalize().getParent();
        Path installDirectory = appDirectory == null ? null : appDirectory.getParent();
        Path nativeLauncher = installDirectory == null ? null
                : installDirectory.resolve("SmartStockServer.exe");
        if (nativeLauncher != null && Files.isRegularFile(nativeLauncher)) {
            return "\"" + nativeLauncher + "\"";
        }
        nativeLauncher=installDirectory==null?null:installDirectory.resolve("SmartStock.exe");
        if(nativeLauncher!=null&&Files.isRegularFile(nativeLauncher))return "\""+nativeLauncher+"\" --sync-service";
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe")
                .toAbsolutePath().normalize();
        return "\"" + javaExecutable.toString().replace('/', '\\')
                + "\" -jar \"" + jarName + "\" --sync-service";
    }

    private static String unixPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static void restartInstalledSyncService() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            runPowerShell("Start-ScheduledTask -TaskName SmartStockServerService -ErrorAction Stop",
                    Duration.ofSeconds(30));
        } else {
            runShell("launchctl kickstart -k \"gui/$(id -u)/com.smartstock.sync\"", Duration.ofSeconds(30));
        }
    }

    public static CommandResult syncServiceStatus() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return runPowerShell("""
                    $current = Get-ScheduledTask -TaskName SmartStockServerService -ErrorAction SilentlyContinue
                    if ($current) { $current | Format-List TaskName,State; exit 0 }
                    $legacy = Get-ScheduledTask -TaskName SmartStockBackgroundSync -ErrorAction SilentlyContinue
                    if ($legacy) { $legacy | Format-List TaskName,State; exit 0 }
                    Write-Output 'SmartStock Windows service is not installed.'
                    exit 0
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
                || lower.contains("taskname:") && lower.contains("smartstockserverservice")
                || lower.contains("taskname:") && lower.contains("smartstockbackgroundsync")
                || lower.contains("task to run:") && lower.contains("run-smartstock-sync-service");
    }

    static boolean isSyncServiceRunning(String statusOutput) {
        if (statusOutput == null || statusOutput.isBlank()) return false;
        String lower = statusOutput.toLowerCase(Locale.ROOT);
        return lower.matches("(?s).*state\\s*:\\s*running.*")
                || lower.contains("status: running")
                || lower.contains("state = running");
    }

    public static CommandResult runInstallerRepair(Path installerPath, String mode) throws Exception {
        if (isWindows()) {
            String script = "& " + powerShellSingleQuoted(installerPath.toAbsolutePath().toString())
                    + " " + shellWord(mode) + "; exit $LASTEXITCODE";
            return runPowerShell(script, Duration.ofMinutes(20));
        }
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
                exec java -Djava.awt.headless=true -Dapple.awt.UIElement=true -jar "$JAR_NAME" --sync-service
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
                $Jar = $null
                foreach ($SearchDir in @((Join-Path $AppDir 'app'), (Join-Path $AppDir 'target'), $AppDir)) {
                  if (-not (Test-Path -LiteralPath $SearchDir -PathType Container)) { continue }
                  $Jar = Get-ChildItem -LiteralPath $SearchDir -Filter 'inventory-management-*.jar' |
                    Sort-Object LastWriteTime -Descending |
                    Select-Object -First 1
                  if ($null -ne $Jar) { break }
                }
                if ($null -eq $Jar) {
                  throw "No SmartStock jar found in $AppDir\\target. Run mvn package first."
                }
                Remove-Item -Force (Join-Path $ServiceAppDir 'inventory-management-*.jar') -ErrorAction SilentlyContinue
                Copy-Item -Force $Jar.FullName $ServiceAppDir
                $SourceDependency = Join-Path $Jar.DirectoryName 'dependency'
                $TargetDependency = Join-Path $ServiceAppDir 'dependency'
                if (Test-Path $SourceDependency) {
                  Remove-Item -Recurse -Force $TargetDependency -ErrorAction SilentlyContinue
                  Copy-Item -Recurse -Force $SourceDependency $TargetDependency
                }
                $Cmd = Join-Path $ServiceDir 'run-smartstock-sync-service.cmd'
                $JarName = Split-Path -Leaf $Jar.FullName
                Set-Content -Path $Cmd -Encoding ASCII -Value @(
                  '@echo off',
                  ('cd /d "' + $ServiceAppDir + '"'),
                  ('java -jar "' + $JarName + '" --sync-service')
                )
                Unregister-ScheduledTask -TaskName SmartStockBackgroundSync `
                  -Confirm:$false -ErrorAction SilentlyContinue
                schtasks /Create /TN SmartStockServerService /TR "`"$Cmd`"" /SC ONSTART /F
                schtasks /Run /TN SmartStockServerService
                schtasks /Query /TN SmartStockServerService /FO LIST
                """.formatted(powerShellSingleQuoted(appDir.toString()));
        return runPowerShell(script, Duration.ofMinutes(2));
    }

    private static CommandResult runShell(String script, Duration timeout) throws Exception {
        return runShell(script, timeout, Map.of());
    }

    private static CommandResult runShell(String script, Duration timeout, Map<String, String> environment) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-lc", script);
        builder.environment().putAll(environment);
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
        return runPowerShell(script, timeout, Map.of());
    }

    private static CommandResult runPowerShell(String script, Duration timeout, Map<String, String> environment) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script);
        builder.environment().putAll(environment);
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

    public record ServerPrerequisites(
            boolean javaReady,
            int javaVersion,
            boolean postgresReady,
            int postgresVersion,
            String details) {
    }

    public static boolean isWindowsRuntime() {
        return isWindows();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
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

    private static String powerShellLiteralValue(String value) {
        return value == null ? "" : value.replace("`", "``")
                .replace("\"", "`\"");
    }
}
