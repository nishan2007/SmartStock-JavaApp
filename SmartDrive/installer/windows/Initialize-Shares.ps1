[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][string]$ConfigPath,
    [Parameter(Mandatory)][string[]]$ClientNetworks,
    [hashtable]$ScannerPasswords = @{},
    [string]$NodePath = (Get-Command node.exe -ErrorAction Stop).Source
)
$ErrorActionPreference = 'Stop'
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run share setup as an administrator inside the SmartDrive guest.'
}
$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$source = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$branding = & $NodePath (Join-Path $source 'src\branding.js') $ConfigPath
if ($LASTEXITCODE) { throw 'Branding configuration is invalid.' }
$brand = ($branding -join [Environment]::NewLine) | ConvertFrom-Json
$root = [IO.Path]::GetFullPath($brand.dataRoot)
$share = $brand.shareName
if (Test-Path -LiteralPath $root) { throw 'Choose a new data root to preserve existing folder permissions.' }
if (Get-SmbShare -Name $share -ErrorAction SilentlyContinue) { throw 'The staff share already exists.' }
if (-not $config.storeRoots -or -not $config.scanStateRoot -or -not $config.backupStateRoot -or -not $config.recoveryRoot) {
    throw 'Configure store roots and private scan, backup and recovery folders.'
}
$stores = @($config.storeRoots.PSObject.Properties)
$plannedGroups = @()
foreach ($store in $stores) {
    if ($store.Name -notmatch '^[1-9][0-9]{0,8}$') { throw 'Invalid store ID.' }
    $expected = Join-Path $root ('Stores\' + $store.Name)
    if ([IO.Path]::GetFullPath($store.Value) -ne $expected) { throw 'Store roots must use the data root Stores folder and store ID.' }
    $plannedGroups += 'SDStore' + $store.Name
}
foreach ($group in $plannedGroups) {
    if (Get-LocalGroup -Name $group -ErrorAction SilentlyContinue) { throw "The local group $group already exists." }
}
$private = Join-Path $root 'Private'
$privatePaths = @($config.scanStateRoot, $config.backupStateRoot, $config.recoveryRoot)
if ($config.office) { $privatePaths += $config.office.cacheRoot }
foreach ($path in $privatePaths) {
    $full = [IO.Path]::GetFullPath($path)
    if (-not $full.StartsWith($private + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Private state and recovery must be under the data root Private folder.'
    }
}
$scanners = @($config.scanners | Where-Object { $null -ne $_ })
if (@($scanners.id | Sort-Object -Unique).Count -ne $scanners.Count -or
    @($scanners.account | Sort-Object -Unique).Count -ne $scanners.Count) {
    throw 'Scanner IDs and accounts must be unique.'
}
if ($scanners.Count -and (Get-SmbShare ScanIntake -ErrorAction SilentlyContinue)) { throw 'ScanIntake already exists.' }
foreach ($scanner in $scanners) {
    if ($scanner.id -notmatch '^[A-Za-z0-9][A-Za-z0-9_-]{0,47}$' -or
        $scanner.account -notmatch '^sdscan_[a-z0-9_]{1,12}$' -or
        $scanner.storeId -notin $stores.Name -or
        [IO.Path]::GetFullPath($scanner.intake) -ne (Join-Path $private ('Intake\' + $scanner.id))) {
        throw 'Invalid scanner ID, account, store or intake path.'
    }
    if (Get-LocalUser -Name $scanner.account -ErrorAction SilentlyContinue) { throw 'Scanner account already exists.' }
    if (-not $WhatIfPreference -and $ScannerPasswords[$scanner.id] -isnot [Security.SecureString]) {
        throw 'Supply a SecureString password for each scanner ID.'
    }
}
function Set-PrivateFolder([string]$Path) {
    [void][IO.Directory]::CreateDirectory($Path)
    & icacls.exe $Path /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)M' | Out-Null
    if ($LASTEXITCODE) { throw 'Could not protect a SmartDrive directory.' }
}
if ($PSCmdlet.ShouldProcess($root, 'Create store shares, scanner intake, discovery and scoped firewall rules')) {
    Set-PrivateFolder $root
    Set-PrivateFolder $private
    foreach ($path in $privatePaths) { Set-PrivateFolder $path }
    foreach ($store in $stores) {
        $group = 'SDStore' + $store.Name
        New-LocalGroup -Name $group -Description ('SmartDrive store ' + $store.Name) | Out-Null
        Set-PrivateFolder $store.Value
        & icacls.exe $store.Value /grant ($group + ':(OI)(CI)M') | Out-Null
        if ($LASTEXITCODE) { throw 'Could not grant store access.' }
        & icacls.exe $root /grant ($group + ':RX') | Out-Null
        if ($LASTEXITCODE) { throw 'Could not grant share traversal.' }
        [void][IO.Directory]::CreateDirectory((Join-Path $store.Value 'Studio Jobs'))
        [void][IO.Directory]::CreateDirectory((Join-Path $store.Value 'Scans'))
    }
    $staffRoot = Join-Path $root 'Stores'
    & icacls.exe $staffRoot /grant ($plannedGroups | ForEach-Object { $_ + ':RX' }) | Out-Null
    if ($LASTEXITCODE) { throw 'Could not grant store folder listing.' }
    New-SmbShare -Name $share -Path $staffRoot -ChangeAccess $plannedGroups -FolderEnumerationMode AccessBased -CachingMode None | Out-Null
    if ($scanners.Count) {
        $intakeRoot = Join-Path $private 'Intake'
        Set-PrivateFolder $intakeRoot
        foreach ($scanner in $scanners) {
            $description='SmartDrive scanner '+$scanner.id
            New-LocalUser -Name $scanner.account -Password $ScannerPasswords[$scanner.id] -Description ($description.Substring(0,[Math]::Min(48,$description.Length))) -PasswordNeverExpires -UserMayNotChangePassword | Out-Null
            Set-PrivateFolder $scanner.intake
            & icacls.exe $scanner.intake /grant ($scanner.account + ':(OI)(CI)M') | Out-Null
            if ($LASTEXITCODE) { throw 'Could not grant scanner intake access.' }
            & icacls.exe $intakeRoot /grant ($scanner.account + ':RX') | Out-Null
            if ($LASTEXITCODE) { throw 'Could not grant scanner traversal.' }
        }
        New-SmbShare -Name ScanIntake -Path $intakeRoot -ChangeAccess $scanners.account -FolderEnumerationMode AccessBased -CachingMode None | Out-Null
    }
    New-NetFirewallRule -Name SmartDrive-SMB -DisplayName 'SmartDrive shop SMB' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 445 -Profile Private,Domain -RemoteAddress $ClientNetworks | Out-Null
    New-NetFirewallRule -Name SmartDrive-Discovery -DisplayName 'SmartDrive device discovery' -Direction Inbound -Action Allow -Protocol UDP -LocalPort 3702 -Profile Private,Domain -RemoteAddress $ClientNetworks | Out-Null
    New-NetFirewallRule -Name SmartDrive-DiscoveryMetadata -DisplayName 'SmartDrive discovery metadata' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5357 -Profile Private,Domain -RemoteAddress $ClientNetworks | Out-Null
    foreach ($name in @('FDResPub', 'fdPHost')) { Set-Service $name -StartupType Automatic; Start-Service $name }
    Write-Output 'Shares provisioned. Enroll workstation identities into their SDStore groups and verify discovery and access from a separate PC.'
}
