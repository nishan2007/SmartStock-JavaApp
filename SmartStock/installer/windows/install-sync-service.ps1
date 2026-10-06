param(
    [string]$AppDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [string]$TaskName = "SmartStockServerService",
    [ValidateSet("development", "test", "production")]
    [string]$Environment = "development",
    [string]$SupabaseUrl = "",
    [string]$SupabasePublishableKey = "",
    [string]$ServiceUser = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ServiceUser)) {
    $ServiceUser = (Get-CimInstance Win32_ComputerSystem).UserName
}
if ([string]::IsNullOrWhiteSpace($ServiceUser)) {
    throw "The signed-in Windows user could not be identified."
}
$serviceSid = (New-Object Security.Principal.NTAccount($ServiceUser)).Translate(
    [Security.Principal.SecurityIdentifier]).Value
$profileKey = "Registry::HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion\ProfileList\$serviceSid"
$serviceHome = [Environment]::ExpandEnvironmentVariables(
    (Get-ItemProperty -LiteralPath $profileKey -Name ProfileImagePath -ErrorAction Stop).ProfileImagePath)
if ([string]::IsNullOrWhiteSpace($serviceHome) -or -not (Test-Path -LiteralPath $serviceHome)) {
    throw "The signed-in Windows user profile could not be resolved."
}
$serviceDir = Join-Path $serviceHome ".smartstock\sync-service"
$serviceAppDir = Join-Path $serviceDir "app"
New-Item -ItemType Directory -Force -Path $serviceAppDir | Out-Null

$existingTask = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($existingTask) {
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction Stop
}
$installRoot = [IO.Path]::GetFullPath((Join-Path $env:ProgramFiles "SmartStock"))
$existingServers = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object {
        $_.Name -match '^javaw?\.exe$' -and
        $_.CommandLine -match '(?i)(^|\s)--sync-service(\s|$)' -and
        $_.ExecutablePath -and
        [IO.Path]::GetFullPath($_.ExecutablePath).StartsWith(
            $installRoot, [StringComparison]::OrdinalIgnoreCase)
    })
foreach ($server in $existingServers) {
    Invoke-CimMethod -InputObject $server -MethodName Terminate | Out-Null
}
$serverDeadline = (Get-Date).AddSeconds(15)
do {
    Start-Sleep -Milliseconds 250
    $remainingServers = @($existingServers | Where-Object {
        Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue
    })
} while ($remainingServers.Count -gt 0 -and (Get-Date) -lt $serverDeadline)
if ($remainingServers.Count -gt 0) {
    throw "The existing SmartStock server process did not stop before update."
}
$tunnelPath = [IO.Path]::GetFullPath((Join-Path $serviceAppDir "dependency\cloudflared\windows-amd64\cloudflared.exe"))
$existingTunnels = @(Get-CimInstance Win32_Process -Filter "Name='cloudflared.exe'" -ErrorAction SilentlyContinue |
    Where-Object {
        $_.ExecutablePath -and
        [IO.Path]::GetFullPath($_.ExecutablePath).Equals(
            $tunnelPath, [StringComparison]::OrdinalIgnoreCase)
    })
foreach ($tunnel in $existingTunnels) {
    Stop-Process -Id $tunnel.ProcessId -Force -ErrorAction Stop
}
Unregister-ScheduledTask -TaskName SmartStockBackgroundSync `
    -Confirm:$false -ErrorAction SilentlyContinue

$jar = Get-ChildItem -Path (Join-Path $AppDir "target") -Filter "inventory-management-*.jar" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $jar) {
    throw "No SmartStock jar found in $AppDir\target. Run mvn package first."
}
$sourceDependency = Join-Path $AppDir "target\dependency"
if (-not (Test-Path $sourceDependency -PathType Container)) {
    throw "The SmartStock dependency payload is missing."
}
$stagedAppDir = Join-Path $serviceDir (".app-staged-" + [guid]::NewGuid())
$previousAppDir = Join-Path $serviceDir (".app-previous-" + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $stagedAppDir | Out-Null
Copy-Item -LiteralPath $jar.FullName -Destination $stagedAppDir -Force
Copy-Item -LiteralPath $sourceDependency -Destination (Join-Path $stagedAppDir "dependency") -Recurse -Force
if (-not (Get-ChildItem (Join-Path $stagedAppDir "dependency") -Filter "postgresql-*.jar" -File)) {
    throw "The staged SmartStock server payload is missing the PostgreSQL driver."
}
try {
    Move-Item -LiteralPath $serviceAppDir -Destination $previousAppDir -Force
    try {
        Move-Item -LiteralPath $stagedAppDir -Destination $serviceAppDir -Force
    } catch {
        Move-Item -LiteralPath $previousAppDir -Destination $serviceAppDir -Force
        throw
    }
    Remove-Item -LiteralPath $previousAppDir -Recurse -Force
} finally {
    Remove-Item -LiteralPath $stagedAppDir -Recurse -Force -ErrorAction SilentlyContinue
}

$jarName = Split-Path -Leaf $jar.FullName
$bundledJava = Join-Path $AppDir "runtime\bin\java.exe"
$java = if (Test-Path -LiteralPath $bundledJava -PathType Leaf) {
    $bundledJava
} else {
    (Get-Command java -ErrorAction Stop).Source
}
$launcher = Join-Path $serviceDir 'run-smartstock-sync-service.cmd'
$serviceLog = Join-Path $serviceDir 'sync-service.log'
$batchJava = $java.Replace('%','%%')
$batchHome = $serviceHome.Replace('%','%%')
$batchApp = $serviceAppDir.Replace('%','%%')
$batchLog = $serviceLog.Replace('%','%%')
$serviceEnvironment = if ($Environment -eq 'test') { 'development' } else { $Environment }
Set-Content -LiteralPath $launcher -Encoding Default -Value @"
@echo off
setlocal
cd /d "$batchApp"
set "SMARTSTOCK_SERVER_JAR="
for %%F in (inventory-management-*.jar) do if exist "%%F" set "SMARTSTOCK_SERVER_JAR=%%F"
if not defined SMARTSTOCK_SERVER_JAR exit /b 2
echo [%date% %time%] Starting SmartStock background server using "$batchJava" and %SMARTSTOCK_SERVER_JAR% >> "$batchLog"
"$batchJava" -Duser.home="$batchHome" -Dsmartstock.environment=$serviceEnvironment -jar "%SMARTSTOCK_SERVER_JAR%" --sync-service >> "$batchLog" 2>&1
set "SMARTSTOCK_EXIT=%errorlevel%"
echo [%date% %time%] Server exited with code %SMARTSTOCK_EXIT% >> "$batchLog"
exit /b %SMARTSTOCK_EXIT%
"@
$serviceExecutable = Join-Path $env:SystemRoot 'System32\cmd.exe'
$serviceArguments = "/d /s /c `"`"$launcher`"`""
$serviceShortcut = Join-Path $serviceDir "SmartStockServer.lnk"
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($serviceShortcut)
$shortcut.TargetPath = $serviceExecutable
$shortcut.Arguments = $serviceArguments
$shortcut.WorkingDirectory = $serviceAppDir
$shortcut.WindowStyle = 7
$shortcut.Save()

$taskAction = New-ScheduledTaskAction -Execute $serviceExecutable -Argument $serviceArguments `
    -WorkingDirectory $serviceAppDir
$taskTrigger = New-ScheduledTaskTrigger -AtLogOn -User $ServiceUser
$taskPrincipal = New-ScheduledTaskPrincipal -UserId $ServiceUser `
    -LogonType Interactive -RunLevel Limited
$taskSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName $TaskName -Action $taskAction -Trigger $taskTrigger -Settings $taskSettings `
    -Principal $taskPrincipal -Description "SmartStock HTTPS LAN and synchronization service" `
    -Force -ErrorAction Stop | Out-Null
Start-ScheduledTask -TaskName $TaskName -ErrorAction Stop
Get-ScheduledTask -TaskName $TaskName -ErrorAction Stop | Format-List TaskName,State

Write-Host "SmartStock Server Service task installed: $TaskName"
Write-Host "The service provides the HTTPS LAN API on port 8443 and background cloud sync."
Write-Host "Service user: $ServiceUser"
