param([string]$StorefrontDirectory = (Join-Path $env:USERPROFILE '.smartstock\storefront-tunnel'))
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Wrangler = Join-Path $Root 'cloudflare\smartstock-update-download\node_modules\wrangler\bin\wrangler.js'
$Profile = Join-Path $env:USERPROFILE '.smartstock\profiles\production'
$Destination = Join-Path $Profile 'installer-portal'
$WebsiteConfig = Join-Path $StorefrontDirectory 'wrangler.website.json'
$SavedWebsite = Join-Path $StorefrontDirectory 'website-config.dpapi'
if (-not (Test-Path -LiteralPath $Wrangler)) { throw 'Install the update-download Worker dependencies first.' }
Add-Type -AssemblyName System.Security
$Website = Get-Content -Raw -LiteralPath $WebsiteConfig | ConvertFrom-Json
$Origins = @($Website.vars.ORIGINS_JSON | ConvertFrom-Json)
if ($Origins.Count -eq 0) { throw 'Configure registered store website origins first.' }
$OriginKeys = @{}
if ($env:SMARTSTOCK_INSTALLER_ORIGIN_KEYS_JSON) {
    $SavedKeys = $env:SMARTSTOCK_INSTALLER_ORIGIN_KEYS_JSON | ConvertFrom-Json
    foreach ($Entry in $SavedKeys.PSObject.Properties) { $OriginKeys[$Entry.Name] = $Entry.Value }
} else {
    if ($Origins.Count -ne 1) { throw 'Provide SMARTSTOCK_INSTALLER_ORIGIN_KEYS_JSON for all configured store origins.' }
    $WebsiteBytes = [Security.Cryptography.ProtectedData]::Unprotect(
        [Convert]::FromBase64String((Get-Content -Raw -LiteralPath $SavedWebsite).Trim()),
        $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    try {
        $Settings = [Text.Encoding]::UTF8.GetString($WebsiteBytes) | ConvertFrom-Json
        $KeyBytes = [Text.Encoding]::UTF8.GetBytes($Settings.edgeKey)
        $Hmac = [Security.Cryptography.HMACSHA256]::new($KeyBytes)
        try {
            $DerivedBytes = $Hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes('smartstock-installer-auth-v1'))
            $OriginKeys[[string]$Origins[0].storeId] = ([BitConverter]::ToString($DerivedBytes)).Replace('-','').ToLowerInvariant()
        } finally { $Hmac.Dispose(); [Array]::Clear($KeyBytes,0,$KeyBytes.Length) }
    } finally { [Array]::Clear($WebsiteBytes,0,$WebsiteBytes.Length); $Settings = $null }
}
$Stores = @($Origins | ForEach-Object {
    if (-not $OriginKeys.ContainsKey([string]$_.storeId) -or $OriginKeys[[string]$_.storeId].Length -lt 32) { throw 'A configured store is missing its installer origin secret.' }
    @{id=[int]$_.storeId;url=$_.url}
})
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$PublisherCredential = Join-Path $Profile 'installer-publish-key.dpapi'
if (-not (Test-Path -LiteralPath $PublisherCredential)) {
    $Random = New-Object byte[] 48
    $Generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $Generator.GetBytes($Random) } finally { $Generator.Dispose() }
    $PublisherBytes = [Text.Encoding]::UTF8.GetBytes([Convert]::ToBase64String($Random))
    try {
        $Encrypted = [Security.Cryptography.ProtectedData]::Protect($PublisherBytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
        Set-Content -LiteralPath $PublisherCredential -Encoding ASCII -Value ([Convert]::ToBase64String($Encrypted))
    } finally { [Array]::Clear($PublisherBytes,0,$PublisherBytes.Length); [Array]::Clear($Random,0,$Random.Length) }
}
$PublisherPlain = [Security.Cryptography.ProtectedData]::Unprotect(
    [Convert]::FromBase64String((Get-Content -Raw -LiteralPath $PublisherCredential).Trim()),
    $null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
try {
    # Keep identities and secrets outside the checkout. The source config has no live origins.
    $Config = @{
        name='smartstock-installer-portal';main=(Join-Path $Root 'cloudflare\smartstock-installer-portal\src\index.js');
        account_id=$Website.account_id;compatibility_date='2026-10-01';workers_dev=$false;preview_urls=$false;
        routes=@(@{pattern='downloads.deckers.gy';custom_domain=$true});
        vars=@{STORES_JSON=(ConvertTo-Json -InputObject $Stores -Compress);INSTALLERS_JSON='{}'};
        r2_buckets=@(@{binding='UPDATE_BUCKET';bucket_name='smartstock-updates'});
        ratelimits=@(@{name='LOGIN_LIMITER';namespace_id='1001';simple=@{limit=10;period=60}})
    }
    $ConfigFile = Join-Path $Destination 'wrangler.json'
    $Config | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $ConfigFile -Encoding UTF8
    $Secrets = @{
        INSTALLER_ORIGIN_KEYS_JSON=($OriginKeys | ConvertTo-Json -Compress);
        INSTALLER_PUBLISH_KEY=[Text.Encoding]::UTF8.GetString($PublisherPlain)
    }
    $Secrets | ConvertTo-Json -Compress | & node $Wrangler secret bulk --config $ConfigFile
    if ($LASTEXITCODE -ne 0) { throw 'The installer portal secret configuration failed.' }
    & node $Wrangler deploy --config $ConfigFile
    if ($LASTEXITCODE -ne 0) { throw 'The installer portal deployment failed.' }
    Write-Host "Deployed https://downloads.deckers.gy for $($Stores.Count) configured store origin(s)."
    Write-Host 'Installer downloads are public and do not require store authentication.'
} finally { [Array]::Clear($PublisherPlain,0,$PublisherPlain.Length); $Secrets=$null; $OriginKeys.Clear() }
