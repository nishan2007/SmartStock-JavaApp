param(
    [Parameter(Mandatory = $true)]
    [string]$SupabaseUrl,
    [Parameter(Mandatory = $true)]
    [string]$SupabasePublishableKey,
    [string]$AppDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [string]$LanSubnet = "LocalSubnet",
    [int]$LanApiPort = 8443
)

$ErrorActionPreference = "Stop"
$developmentProject = "wbffhygkttoaaodjcvuh"

if (-not ([Security.Principal.WindowsPrincipal]
        [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Run this production installer from an elevated PowerShell window."
}
if ($SupabaseUrl -notmatch '^https://([a-z0-9]{20})\.supabase\.co/?$') {
    throw "SupabaseUrl must be a hosted project URL such as https://projectref.supabase.co."
}
if ($Matches[1] -eq $developmentProject) {
    throw "Production cannot use the SmartStock development Supabase project."
}
if ([string]::IsNullOrWhiteSpace($SupabasePublishableKey)) {
    throw "A production Supabase publishable key is required."
}
foreach ($command in @("java", "psql", "schtasks")) {
    if ($null -eq (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command is not installed or not on PATH: $command"
    }
}
$javaVersion = (& java -version 2>&1 | Select-Object -First 1)
if ($javaVersion -notmatch '"(1[7-9]|[2-9][0-9])') {
    throw "Java 17 or later is required. Detected: $javaVersion"
}
$jar = Get-ChildItem -Path (Join-Path $AppDir "target") -Filter "inventory-management-*.jar" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $jar) {
    throw "Build SmartStock before installing the production service."
}
$configPath = Join-Path $env:USERPROFILE ".smartstock\database.properties"
if (-not (Test-Path $configPath)) {
    throw "Provision SERVER mode through SmartStock Database Setup before installing the service."
}
$configText = Get-Content -Raw $configPath
if ($configText -notmatch '(?m)^mode=SERVER\s*$') {
    throw "Database Setup is not configured in SERVER mode."
}
if ($configText -notmatch '(?m)^jdbc\.url=jdbc\\:postgresql\\://(127\.0\.0\.1|localhost|\[\\:\\:1\])') {
    throw "The server JDBC URL must use loopback."
}

$serviceInstaller = Join-Path $PSScriptRoot "install-sync-service.ps1"
& $serviceInstaller -AppDir $AppDir -TaskName "SmartStockServerService" `
    -Environment "production" -SupabaseUrl $SupabaseUrl.TrimEnd("/") `
    -SupabasePublishableKey $SupabasePublishableKey

$ruleName = "SmartStock LAN API 8443"
$existingRule = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if ($null -ne $existingRule) {
    Remove-NetFirewallRule -DisplayName $ruleName
}
New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow `
    -Protocol TCP -LocalPort $LanApiPort -RemoteAddress $LanSubnet `
    -Profile Private | Out-Host

Start-Sleep -Seconds 3
$task = schtasks /Query /TN "SmartStockServerService" /FO LIST
if ($LASTEXITCODE -ne 0) {
    throw "SmartStockServerService was not installed."
}
$listener = Get-NetTCPConnection -LocalPort $LanApiPort -State Listen -ErrorAction SilentlyContinue
if ($null -eq $listener) {
    throw "SmartStock service is installed but is not listening on port $LanApiPort. Check the service log."
}

Write-Host "Production server service installed and listening on port $LanApiPort."
Write-Host "Run ProductionReadinessMain only after the identity migration and recovery drill."
