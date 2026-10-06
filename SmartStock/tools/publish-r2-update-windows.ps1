param(
    [Parameter(Mandatory = $true)][string]$Artifact,
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][int]$BuildNumber,
    [Parameter(Mandatory = $true)][string]$ReleaseNotes,
    [string]$Installer
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Profile = Join-Path $env:USERPROFILE '.smartstock\profiles\production'
$SupabaseProperties = Join-Path $Profile 'supabase.properties'
$CredentialFile = Join-Path $Profile 'server-cloud-credential.dpapi'
$InstallerCredentialFile = Join-Path $Profile 'installer-publish-key.dpapi'
if ([string]::IsNullOrWhiteSpace($Installer)) {
    $CandidateInstaller = Join-Path (Split-Path -Parent ([IO.Path]::GetFullPath($Artifact))) "smartstock-windows-setup-$Version.exe"
    if (Test-Path -LiteralPath $CandidateInstaller) { $Installer = $CandidateInstaller }
}
if ((Get-Item -LiteralPath $Artifact).Length -gt 300 * 1024 * 1024) {
    & (Join-Path $PSScriptRoot 'publish-verified-release-windows.ps1') -Artifact $Artifact -Version $Version -BuildNumber $BuildNumber -Kind update -ReleaseNotes $ReleaseNotes
    if ($Installer) {
        & (Join-Path $PSScriptRoot 'publish-installer-windows.ps1') -Installer $Installer -Version $Version -BuildNumber $BuildNumber
    }
    return
}
$PreviousSupabaseUrl = $env:SUPABASE_URL
$PreviousSupabaseKey = $env:SUPABASE_SECRET_KEY
$PreviousInstallerKey = $env:SMARTSTOCK_INSTALLER_PUBLISH_KEY

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
    if ($Installer -and -not $env:SMARTSTOCK_INSTALLER_PUBLISH_KEY) {
        if (-not (Test-Path -LiteralPath $InstallerCredentialFile)) { throw 'Configure the protected installer publishing key before publishing this release.' }
        $InstallerPlainBytes = [Security.Cryptography.ProtectedData]::Unprotect(
            [Convert]::FromBase64String((Get-Content -Raw -LiteralPath $InstallerCredentialFile).Trim()),
            $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
        $env:SMARTSTOCK_INSTALLER_PUBLISH_KEY = [Text.Encoding]::UTF8.GetString($InstallerPlainBytes).Trim()
    }
    # Windows PowerShell 5 does not expose ProcessStartInfo.ArgumentList.
    # Set the protected values only in this process environment and let Git
    # Bash receive the arguments through its normal argv handling.
    $env:SUPABASE_URL = $SupabaseUrl
    $env:SUPABASE_SECRET_KEY = $Secret
    Push-Location $Root
    try {
        $PublishArguments = @('./tools/publish-r2-update.sh', $Artifact, $Version, $BuildNumber.ToString(), 'windows', $ReleaseNotes)
        if ($Installer) { $PublishArguments += $Installer }
        & 'C:\Program Files\Git\bin\bash.exe' @PublishArguments
        if ($LASTEXITCODE -ne 0) { throw "Publishing failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }
} finally {
    $env:SUPABASE_URL = $PreviousSupabaseUrl
    $env:SUPABASE_SECRET_KEY = $PreviousSupabaseKey
    $env:SMARTSTOCK_INSTALLER_PUBLISH_KEY = $PreviousInstallerKey
    if ($InstallerPlainBytes) { [Array]::Clear($InstallerPlainBytes, 0, $InstallerPlainBytes.Length) }
    if ($PlainBytes) { [Array]::Clear($PlainBytes, 0, $PlainBytes.Length) }
    $Secret = $null
}
