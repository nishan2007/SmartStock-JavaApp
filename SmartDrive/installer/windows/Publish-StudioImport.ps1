[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][string]$ImportRoot,
    [Parameter(Mandatory)][string]$ConfigPath,
    [Parameter(Mandatory)][string]$CatalogPath,
    [switch]$MaintenanceWindow
)
$ErrorActionPreference = 'Stop'
if (-not $MaintenanceWindow) { throw 'Freeze legacy writes and staff share writes, then use MaintenanceWindow.' }
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Publish imports from an administrator session in the guest.' }
function Assert-PlainPath([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    while ($null -ne $item) {
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Linked import or destination path.' }
        if ($item -is [IO.FileInfo]) { $item = $item.Directory }
        else { $item = $item.Parent }
    }
}
$ImportRoot = (Resolve-Path -LiteralPath $ImportRoot).Path
Assert-PlainPath $ConfigPath
Assert-PlainPath (Join-Path $ImportRoot 'catalog.json')
$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$catalog = Get-Content -LiteralPath (Join-Path $ImportRoot 'catalog.json') -Raw | ConvertFrom-Json
if ($catalog.formatVersion -ne 1 -or $null -eq $catalog.records) { throw 'Invalid import catalog.' }
if ($CatalogPath -notmatch '^[A-Za-z]:\\' -or (Test-Path -LiteralPath $CatalogPath)) {
    throw 'Choose a new absolute private catalog file.'
}
$CatalogPath = [IO.Path]::GetFullPath($CatalogPath)
$catalogParent = Split-Path -Path $CatalogPath -Parent
Assert-PlainPath $catalogParent
Assert-PlainPath $ImportRoot
$formats = @{ BADGE='.sstbadge'; BUSINESS_CARDS='.sspub'; LABELS='.sspub'; BILL_BOOKS='.sspub' }
$folders = @{}
$plans = @()
foreach ($record in $catalog.records) {
    $owner = [string]$record.store
    $kind = [string]$record.legacy.kind
    $id = [string]$record.legacy.id
    $extension = $formats[$kind]
    $parts = @($record.parts)
    $rootProperty = $config.storeRoots.PSObject.Properties[$owner]
    if (-not $rootProperty -or -not $extension -or $id -notmatch '^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$' -or
        $owner -notmatch '^[1-9][0-9]{0,8}$' -or $parts.Count -ne 4 -or $parts[0] -ne 'Studio Jobs' -or
        $parts[1] -ne $kind -or $parts[3] -notlike ('*-'+$id+$extension) -or
        $parts[3] -match '[<>:"/\\|?*\x00-\x1f]' -or $record.sha256 -notmatch '^[a-f0-9]{64}$') {
        throw 'Invalid imported design path or identity.'
    }
    $metadataOwner = $record.metadata.ownerStoreId
    if ($null -eq $metadataOwner) { $metadataOwner = $record.legacy.storageStoreId }
    if ([string]$metadataOwner -ne $owner) {
        throw 'Imported owner metadata does not match its path.'
    }
    $access = @((@($owner) + @($record.metadata.sharedStoreIds | ForEach-Object {[string]$_})) | Sort-Object -Unique)
    if ($parts[2] -ne ('Access-'+($access -join '-'))) { throw 'Imported access folder differs from its assignments.' }
    foreach ($store in $access) {
        if ($store -notmatch '^[1-9][0-9]{0,8}$' -or -not $config.storeRoots.PSObject.Properties[$store]) { throw 'Unconfigured shared store.' }
        $null = Get-LocalGroup -Name ('SDStore'+$store) -ErrorAction Stop
    }
    $ownerRoot = (Resolve-Path -LiteralPath $rootProperty.Value).Path
    Assert-PlainPath $ownerRoot
    foreach ($property in $config.storeRoots.PSObject.Properties) {
        $staffRoot = [IO.Path]::GetFullPath($property.Value).TrimEnd('\') + '\'
        if ($CatalogPath.StartsWith($staffRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Keep access metadata outside staff storage.' }
    }
    $source = Join-Path $ImportRoot ('Stores\'+$owner+'\'+($parts -join '\'))
    Assert-PlainPath $source
    if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ine $record.sha256) { throw 'Staged file checksum mismatch.' }
    $accessFolder = Join-Path $ownerRoot (($parts[0..2]) -join '\')
    if (-not $folders.ContainsKey($accessFolder)) {
        if (Test-Path -LiteralPath $accessFolder) { throw 'An imported access folder already exists. Review the prior publication.' }
        $folders[$accessFolder] = @{store=$owner;parts=@($parts[0..2]);storeIds=$access;ownerRoot=$ownerRoot}
    }
    $plans += @{source=$source;target=(Join-Path $accessFolder $parts[3]);sha256=$record.sha256;identity=$record.legacy}
}
if ($PSCmdlet.ShouldProcess($ImportRoot, 'Publish verified designs and store ACLs, then commit the private catalog')) {
    if (-not ('SmartDriveImportIdentityStreamV1' -as [type])) { Add-Type -Path (Join-Path $PSScriptRoot 'IdentityStream.cs') }
    foreach ($path in $folders.Keys) {
        $folder = $folders[$path]
        foreach ($parent in @($folder.ownerRoot,(Join-Path $folder.ownerRoot 'Studio Jobs'),(Split-Path $path -Parent))) {
            [void][IO.Directory]::CreateDirectory($parent)
            Assert-PlainPath $parent
            foreach ($store in $folder.storeIds) {
                & icacls.exe $parent /grant ('SDStore'+$store+':RX') | Out-Null
                if ($LASTEXITCODE) { throw 'Could not grant shared folder traversal.' }
            }
        }
        [void][IO.Directory]::CreateDirectory($path)
        & icacls.exe $path /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)M' | Out-Null
        if ($LASTEXITCODE) { throw 'Could not protect an imported folder.' }
        foreach ($store in $folder.storeIds) {
            & icacls.exe $path /grant ('SDStore'+$store+':(OI)(CI)M') | Out-Null
            if ($LASTEXITCODE) { throw 'Could not assign imported store access.' }
        }
    }
    foreach ($plan in $plans) {
        [IO.File]::Copy($plan.source,$plan.target,$false)
        if ((Get-FileHash -LiteralPath $plan.target -Algorithm SHA256).Hash -ine $plan.sha256) {
            throw 'Published design checksum mismatch. Keep writes frozen and review publication.'
        }
        $identityBytes = [Text.Encoding]::UTF8.GetBytes(($plan.identity | ConvertTo-Json -Compress))
        [SmartDriveImportIdentityStreamV1]::Write($plan.target,$identityBytes)
        if ([Convert]::ToBase64String([SmartDriveImportIdentityStreamV1]::Read($plan.target)) -cne [Convert]::ToBase64String($identityBytes)) {
            throw 'Published identity metadata failed verification.'
        }
        if ((Get-FileHash -LiteralPath $plan.target -Algorithm SHA256).Hash -ine $plan.sha256) { throw 'Identity metadata altered the design bytes.' }
    }
    $catalog.sharedFolders = @($folders.Values | ForEach-Object { @{store=$_.store;parts=$_.parts;storeIds=$_.storeIds} })
    $bytes = [Text.Encoding]::UTF8.GetBytes(($catalog | ConvertTo-Json -Depth 100))
    # Publish the activation catalog only after all bytes and ACLs are verified.
    $stagedCatalog = Join-Path $catalogParent ('import-'+[guid]::NewGuid()+'.tmp')
    try {
        $file = [IO.File]::Open($stagedCatalog,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try { $file.Write($bytes,0,$bytes.Length); $file.Flush($true) } finally { $file.Dispose() }
        [IO.File]::Move($stagedCatalog,$CatalogPath)
    } finally {
        if (Test-Path -LiteralPath $stagedCatalog) { Remove-Item -LiteralPath $stagedCatalog }
    }
    Write-Output 'Verified library published. Configure migrationCatalog and verify store access before reopening writes.'
}
