param(
    [Parameter(Mandatory=$true)][string]$Artifact,
    [Parameter(Mandatory=$true)][string]$Version,
    [Parameter(Mandatory=$true)][int]$BuildNumber,
    [Parameter(Mandatory=$true)][ValidateSet('update','studio')][string]$Kind,
    [Parameter(Mandatory=$true)][string]$ReleaseNotes
)
$ErrorActionPreference='Stop'
$profilePath=Join-Path $env:USERPROFILE '.smartstock\profiles\production'
$oldUrl=$env:SUPABASE_URL;$oldKey=$env:SUPABASE_SECRET_KEY;$oldPublisher=$env:SMARTSTOCK_INSTALLER_PUBLISH_KEY
Add-Type -AssemblyName System.Security
try {
    $urlLine=Get-Content -LiteralPath (Join-Path $profilePath 'supabase.properties') | Where-Object {$_ -match '^url='} | Select-Object -First 1
    if(!$urlLine){throw 'Production update URL is missing.'}
    $env:SUPABASE_URL=$urlLine.Substring(4).Replace('\:',':')
    $cloudBytes=[Security.Cryptography.ProtectedData]::Unprotect([Convert]::FromBase64String((Get-Content -Raw -LiteralPath (Join-Path $profilePath 'server-cloud-credential.dpapi')).Trim()),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    $publisherBytes=[Security.Cryptography.ProtectedData]::Unprotect([Convert]::FromBase64String((Get-Content -Raw -LiteralPath (Join-Path $profilePath 'installer-publish-key.dpapi')).Trim()),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    $env:SUPABASE_SECRET_KEY=[Text.Encoding]::UTF8.GetString($cloudBytes).Trim()
    $env:SMARTSTOCK_INSTALLER_PUBLISH_KEY=[Text.Encoding]::UTF8.GetString($publisherBytes).Trim()
    & node (Join-Path $PSScriptRoot 'publish-verified-release.mjs') ([IO.Path]::GetFullPath($Artifact)) $Version $BuildNumber.ToString() $Kind ([IO.Path]::GetFullPath($ReleaseNotes))
    if($LASTEXITCODE -ne 0){throw 'Verified release publishing failed.'}
}finally{
    $env:SUPABASE_URL=$oldUrl;$env:SUPABASE_SECRET_KEY=$oldKey;$env:SMARTSTOCK_INSTALLER_PUBLISH_KEY=$oldPublisher
    if($cloudBytes){[Array]::Clear($cloudBytes,0,$cloudBytes.Length)}
    if($publisherBytes){[Array]::Clear($publisherBytes,0,$publisherBytes.Length)}
}
