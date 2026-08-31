param([Parameter(Mandatory=$true)][string]$Destination)
$ErrorActionPreference = 'Stop'
$Version = '2026.8.2'
$Expected = 'c29eee2b121f5436a642eed69fd9767da7e7b8c510fa50aaa130337f931357b5'
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$Candidate = Join-Path $Destination ('cloudflared-' + [guid]::NewGuid() + '.download')
try {
    if ($env:SMARTSTOCK_CLOUDFLARED_BUILD_PATH) {
        Copy-Item -LiteralPath $env:SMARTSTOCK_CLOUDFLARED_BUILD_PATH -Destination $Candidate
    } else {
        Invoke-WebRequest -Uri "https://github.com/cloudflare/cloudflared/releases/download/$Version/cloudflared-windows-amd64.exe" -OutFile $Candidate
    }
    if ((Get-FileHash -LiteralPath $Candidate -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Expected) {
        throw 'Cloudflare client hash mismatch. Packaging is blocked.'
    }
    Move-Item -LiteralPath $Candidate -Destination (Join-Path $Destination 'cloudflared.exe') -Force
    Invoke-WebRequest -Uri "https://raw.githubusercontent.com/cloudflare/cloudflared/$Version/LICENSE" -OutFile (Join-Path $Destination 'LICENSE')
    Write-Host "Bundled cloudflared $Version (SHA-256 verified)."
} finally {
    if (Test-Path -LiteralPath $Candidate) { Remove-Item -LiteralPath $Candidate }
}
