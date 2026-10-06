param(
    [string]$Cloudflared = 'cloudflared.exe',
    [string]$TunnelDirectory = (Join-Path $env:USERPROFILE '.smartstock\storefront-tunnel')
)
$ErrorActionPreference = 'Stop'
$ResolvedTunnelDirectory = (Resolve-Path -LiteralPath $TunnelDirectory).Path
$Executable = (Get-Command $Cloudflared -ErrorAction Stop).Source
$PidFile = Join-Path $ResolvedTunnelDirectory 'connector.pid'
if (Test-Path -LiteralPath $PidFile) {
    $ExistingId = 0
    if ([int]::TryParse((Get-Content -Raw -LiteralPath $PidFile).Trim(), [ref]$ExistingId)) {
        $Existing = Get-CimInstance Win32_Process -Filter "ProcessId=$ExistingId"
        if ($Existing -and $Existing.CommandLine -like "*$ResolvedTunnelDirectory*") {
            throw 'The storefront connector is already running.'
        }
    }
}
Add-Type -AssemblyName System.Security
$Protected = [Convert]::FromBase64String((Get-Content -Raw -LiteralPath (Join-Path $ResolvedTunnelDirectory 'tunnel-token.dpapi')).Trim())
$Plain = [Security.Cryptography.ProtectedData]::Unprotect($Protected, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
$PreviousToken = $env:TUNNEL_TOKEN
try {
    $env:TUNNEL_TOKEN = [Text.Encoding]::UTF8.GetString($Plain)
    $Log = Join-Path $ResolvedTunnelDirectory 'connector.log'
    $Process = Start-Process -FilePath $Executable -ArgumentList @('tunnel','--no-autoupdate','--logfile',('"'+$Log+'"'),'run') -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath $PidFile -Value $Process.Id
    Write-Output "Started separate storefront connector, PID $($Process.Id). No Windows service was installed or restarted."
} finally {
    $env:TUNNEL_TOKEN = $PreviousToken
    [Array]::Clear($Plain, 0, $Plain.Length)
}
