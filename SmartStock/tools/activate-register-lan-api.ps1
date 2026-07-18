param([switch]$Confirm)
$ErrorActionPreference = 'Stop'
if (-not $Confirm) { throw 'Run with -Confirm after this register is approved and paired.' }
$appDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$violations = Get-ChildItem (Join-Path $appDir 'src\ui'), (Join-Path $appDir 'src\managers'), (Join-Path $appDir 'src\Receipt') -Recurse -Filter *.java |
    Select-String -Pattern 'DB\.getConnection\(|DriverManager\.getConnection\(' | Select-Object -ExpandProperty Path -Unique
if ($violations) { throw "Register activation blocked; register-callable JDBC remains in this build." }
$credentials = Join-Path $env:USERPROFILE '.smartstock\credentials'
if (-not (Test-Path (Join-Path $credentials 'lan-api-device-token.dpapi'))) {
    throw 'This register has not claimed its approved LAN API credential.'
}
$configPath = Join-Path $env:USERPROFILE '.smartstock\database.properties'
'device-db-user','device-db-password','primary-db-user','primary-db-password' | ForEach-Object {
    Remove-Item -Force (Join-Path $credentials "$_.dpapi") -ErrorAction SilentlyContinue
}
Write-Host 'This register now uses only the authenticated SmartStock LAN service.'
