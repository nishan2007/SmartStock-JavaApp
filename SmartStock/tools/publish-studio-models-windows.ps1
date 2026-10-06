param(
    [Parameter(Mandatory=$true)][string]$ModelDirectory,
    [string]$Catalogue = (Join-Path $PSScriptRoot '..\src\models\catalogue-v1.json')
)
$ErrorActionPreference = 'Stop'
$PreviousPublisher = $env:SMARTSTOCK_INSTALLER_PUBLISH_KEY
try {
    if (-not $env:SMARTSTOCK_INSTALLER_PUBLISH_KEY) {
        $ProtectedKey = Join-Path $env:USERPROFILE '.smartstock\profiles\production\installer-publish-key.dpapi'
        if (-not (Test-Path -LiteralPath $ProtectedKey)) {
            throw 'Configure the protected Deckers publisher key before publishing the Best model.'
        }
        Add-Type -AssemblyName System.Security
        $PublisherBytes = [Security.Cryptography.ProtectedData]::Unprotect(
            [Convert]::FromBase64String((Get-Content -Raw -LiteralPath $ProtectedKey).Trim()),
            $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
        $env:SMARTSTOCK_INSTALLER_PUBLISH_KEY = [Text.Encoding]::UTF8.GetString($PublisherBytes).Trim()
    }
    & node (Join-Path $PSScriptRoot 'publish-studio-models.mjs') ([IO.Path]::GetFullPath($Catalogue)) ([IO.Path]::GetFullPath($ModelDirectory))
    if ($LASTEXITCODE -ne 0) { throw 'Independent model publication failed; do not publish the application release.' }
} finally {
    $env:SMARTSTOCK_INSTALLER_PUBLISH_KEY = $PreviousPublisher
    if ($PublisherBytes) { [Array]::Clear($PublisherBytes, 0, $PublisherBytes.Length) }
}
