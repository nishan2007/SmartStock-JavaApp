param(
    [string]$WebHost = 'studio.deckers.gy',
    [string]$ServerUserHome = $env:USERPROFILE
)
$ErrorActionPreference = 'Stop'
$WebHost = $WebHost.Trim().ToLowerInvariant()
if ($WebHost.Length -gt 253 -or $WebHost -notmatch '^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+deckers\.gy$') {
    throw 'Use a Deckers subdomain such as studio.deckers.gy.'
}
$Directory = Join-Path ([IO.Path]::GetFullPath($ServerUserHome)) '.smartstock'
New-Item -ItemType Directory -Force -Path $Directory | Out-Null
$Settings = Join-Path $Directory 'mobile-web.properties'
if (Test-Path -LiteralPath $Settings) {
    Copy-Item -LiteralPath $Settings -Destination ($Settings + '.backup-' + (Get-Date -Format 'yyyyMMddHHmmss'))
}
Set-Content -LiteralPath $Settings -Encoding ASCII -Value ('host=' + $WebHost)
Write-Host "Configured https://${WebHost}:8444/background-remover.html"
Write-Host 'Create a local DNS record pointing this name to the store server LAN IP before restarting the web app.'
Write-Host 'Stop and start the web app to create its separate browser certificate. The register TLS identity is unchanged.'
Write-Host ('Browser certificate: ' + (Join-Path $Directory ('mobile-web-' + $WebHost + '.cer')))
Write-Host 'Trust only that public certificate on staff computers after checking its SHA-256 fingerprint against the server copy.'
