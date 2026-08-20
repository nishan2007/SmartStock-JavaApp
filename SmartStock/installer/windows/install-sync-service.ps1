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
Unregister-ScheduledTask -TaskName SmartStockBackgroundSync `
    -Confirm:$false -ErrorAction SilentlyContinue

$jar = Get-ChildItem -Path (Join-Path $AppDir "target") -Filter "inventory-management-*.jar" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $jar) {
    throw "No SmartStock jar found in $AppDir\target. Run mvn package first."
}
Get-ChildItem $serviceAppDir -Filter "inventory-management-*.jar" -ErrorAction SilentlyContinue |
    Remove-Item -Force
Copy-Item -LiteralPath $jar.FullName -Destination $serviceAppDir -Force
$sourceDependency = Join-Path $AppDir "target\dependency"
$targetDependency = Join-Path $serviceAppDir "dependency"
if (Test-Path $sourceDependency) {
    Remove-Item -Recurse -Force $targetDependency -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $targetDependency | Out-Null
    Copy-Item -Path (Join-Path $sourceDependency "*") `
        -Destination $targetDependency -Recurse -Force
}

$jarName = Split-Path -Leaf $jar.FullName
$bundledJava = Join-Path $AppDir "runtime\bin\javaw.exe"
$java = if (Test-Path -LiteralPath $bundledJava -PathType Leaf) {
    $bundledJava
} else {
    (Get-Command javaw -ErrorAction Stop).Source
}
$serviceArguments = "-Duser.home=`"$serviceHome`" -jar `"$jarName`" --sync-service"
$serviceShortcut = Join-Path $serviceDir "SmartStockServer.lnk"
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($serviceShortcut)
$shortcut.TargetPath = $java
$shortcut.Arguments = $serviceArguments
$shortcut.WorkingDirectory = $serviceAppDir
$shortcut.WindowStyle = 7
$shortcut.Save()

$taskAction = New-ScheduledTaskAction -Execute (Join-Path $env:WINDIR "explorer.exe") `
    -Argument ("`"$serviceShortcut`"")
$taskTrigger = New-ScheduledTaskTrigger -AtLogOn -User $ServiceUser
$taskPrincipal = New-ScheduledTaskPrincipal -UserId $ServiceUser `
    -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName $TaskName -Action $taskAction -Trigger $taskTrigger `
    -Principal $taskPrincipal -Description "SmartStock HTTPS LAN and synchronization service" `
    -Force -ErrorAction Stop | Out-Null
Start-ScheduledTask -TaskName $TaskName -ErrorAction Stop
Get-ScheduledTask -TaskName $TaskName -ErrorAction Stop | Format-List TaskName,State

Write-Host "SmartStock Server Service task installed: $TaskName"
Write-Host "The service provides the HTTPS LAN API on port 8443 and background cloud sync."
Write-Host "Service user: $ServiceUser"
