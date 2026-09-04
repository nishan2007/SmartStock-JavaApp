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
    $Start = [Diagnostics.ProcessStartInfo]::new()
    $Start.FileName = 'C:\Program Files\Git\bin\bash.exe'
    $Start.WorkingDirectory = $Root
    foreach ($Value in @('./tools/publish-r2-update.sh', $Artifact, $Version,
            $BuildNumber.ToString(), 'windows', $ReleaseNotes)) {
        $Start.ArgumentList.Add($Value)
    }
    $Start.Environment['SUPABASE_URL'] = $SupabaseUrl
    $Start.Environment['SUPABASE_SECRET_KEY'] = $Secret
    $Start.UseShellExecute = $false
    $Process = [Diagnostics.Process]::Start($Start)
    $Process.WaitForExit()
    if ($Process.ExitCode -ne 0) { throw "Publishing failed with exit code $($Process.ExitCode)." }
} finally {
    if ($PlainBytes) { [Array]::Clear($PlainBytes, 0, $PlainBytes.Length) }
    $Secret = $null
}
