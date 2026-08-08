param(
    [string]$AppDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [string]$TaskName = "SmartStockServerService",
    [ValidateSet("development", "test", "production")]
    [string]$Environment = "development",
    [string]$SupabaseUrl = "",
    [string]$SupabasePublishableKey = ""
)

$ErrorActionPreference = "Stop"

$serviceDir = Join-Path $env:USERPROFILE ".smartstock\sync-service"
$serviceAppDir = Join-Path $serviceDir "app"
New-Item -ItemType Directory -Force -Path $serviceAppDir | Out-Null

$jar = Get-ChildItem -Path (Join-Path $AppDir "target") -Filter "inventory-management-*.jar" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $jar) {
    throw "No SmartStock jar found in $AppDir\target. Run mvn package first."
}
Copy-Item -Force $jar.FullName $serviceAppDir
$sourceDependency = Join-Path $AppDir "target\dependency"
$targetDependency = Join-Path $serviceAppDir "dependency"
if (Test-Path $sourceDependency) {
    Remove-Item -Recurse -Force $targetDependency -ErrorAction SilentlyContinue
    Copy-Item -Recurse -Force $sourceDependency $targetDependency
}

$runner = Join-Path $serviceDir "run-smartstock-sync-service.cmd"
$jarName = Split-Path -Leaf $jar.FullName
$runnerLines = @(
    "@echo off",
    "set `"SMARTSTOCK_ENVIRONMENT=$Environment`""
)
if (-not [string]::IsNullOrWhiteSpace($SupabaseUrl)) {
    $runnerLines += "set `"SUPABASE_URL=$SupabaseUrl`""
}
if (-not [string]::IsNullOrWhiteSpace($SupabasePublishableKey)) {
    $runnerLines += "set `"SUPABASE_PUBLISHABLE_KEY=$SupabasePublishableKey`""
}
$runnerLines += @(
    "cd /d `"$serviceAppDir`"",
    "java -jar $jarName --sync-service"
)
Set-Content -Path $runner -Encoding ASCII -Value $runnerLines

schtasks /Create /TN $TaskName /TR "`"$runner`"" /SC ONSTART /RL LIMITED /F | Out-Host
schtasks /Run /TN $TaskName | Out-Host
schtasks /Query /TN $TaskName /FO LIST | Out-Host

Write-Host "SmartStock Server Service task installed: $TaskName"
Write-Host "The service provides the HTTPS LAN API on port 8443 and background cloud sync."
Write-Host "Runner: $runner"
