param(
    [Parameter(Mandatory = $true)][string]$Artifact,
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][int]$BuildNumber,
    [Parameter(Mandatory = $true)][string]$ReleaseNotes
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Profile = Join-Path $env:USERPROFILE '.smartstock\profiles\production'
$SupabaseProperties = Join-Path $Profile 'supabase.properties'
$CredentialFile = Join-Path $Profile 'server-cloud-credential.dpapi'

$UrlLine = Get-Content -LiteralPath $SupabaseProperties |
    Where-Object { $_ -match '^url=' } | Select-Object -First 1
if (-not $UrlLine) { throw 'The production Supabase URL is not configured.' }
$SupabaseUrl = $UrlLine.Substring(4).Replace('\:', ':')
if ($SupabaseUrl -notmatch '^https://') { throw 'The production Supabase URL is invalid.' }

Add-Type -AssemblyName System.Security
$Encrypted = (Get-Content -Raw -LiteralPath $CredentialFile).Trim()
$ProtectedBytes = [Convert]::FromBase64String($Encrypted)
$PlainBytes = [Security.Cryptography.ProtectedData]::Unprotect(
    $ProtectedBytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
$Secret = [Text.Encoding]::UTF8.GetString($PlainBytes).Trim()

try {
    # Windows PowerShell 5 does not expose ProcessStartInfo.ArgumentList.
    # Set the protected values only in this process environment and let Git
    # Bash receive the arguments through its normal argv handling.
    $env:SUPABASE_URL = $SupabaseUrl
    $env:SUPABASE_SECRET_KEY = $Secret
    Push-Location $Root
    try {
        & 'C:\Program Files\Git\bin\bash.exe' './tools/publish-r2-update.sh' $Artifact $Version $BuildNumber.ToString() 'windows' $ReleaseNotes
        if ($LASTEXITCODE -ne 0) { throw "Publishing failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }
} finally {
    $env:SUPABASE_URL = $null
    $env:SUPABASE_SECRET_KEY = $null
    if ($PlainBytes) { [Array]::Clear($PlainBytes, 0, $PlainBytes.Length) }
    $Secret = $null
}
