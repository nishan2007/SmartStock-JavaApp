param(
    [Parameter(Mandatory=$true)][string]$Installer,
    [Parameter(Mandatory=$true)][string]$Version,
    [Parameter(Mandatory=$true)][int]$BuildNumber
)
$ErrorActionPreference='Stop'
$Root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Credential=Join-Path $env:USERPROFILE '.smartstock\profiles\production\installer-publish-key.dpapi'
$Previous=$env:SMARTSTOCK_INSTALLER_PUBLISH_KEY
try {
    if (-not $Previous) {
        Add-Type -AssemblyName System.Security
        $Plain=[Security.Cryptography.ProtectedData]::Unprotect(
            [Convert]::FromBase64String((Get-Content -Raw -LiteralPath $Credential).Trim()),
            $null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
        $env:SMARTSTOCK_INSTALLER_PUBLISH_KEY=[Text.Encoding]::UTF8.GetString($Plain)
    }
    & node (Join-Path $PSScriptRoot 'publish-installer.mjs') ([IO.Path]::GetFullPath($Installer)) $Version $BuildNumber.ToString() windows
    if ($LASTEXITCODE -ne 0) { throw "Installer publishing failed with exit code $LASTEXITCODE." }
} finally {
    $env:SMARTSTOCK_INSTALLER_PUBLISH_KEY=$Previous
    if($Plain){[Array]::Clear($Plain,0,$Plain.Length)}
}
