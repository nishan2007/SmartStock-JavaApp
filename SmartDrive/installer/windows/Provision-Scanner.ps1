$ErrorActionPreference = 'Stop'
# Invoked only by the account broker. Passwords arrive over stdin, never argv.
$request = [Console]::In.ReadToEnd() | ConvertFrom-Json
$appRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$policy = Get-Content -LiteralPath (Join-Path $appRoot 'account-stores.json') -Raw | ConvertFrom-Json
if ($request.id -cnotmatch '^[A-Za-z0-9][A-Za-z0-9_-]{0,47}$' -or
    $request.account -cnotmatch '^sdscan_[a-z0-9_]{1,12}$' -or
    $request.password -cnotmatch '^[A-Za-z0-9!_-]{32,100}$' -or
    [string]$request.store -notin $policy.storeIds -or $policy.dataRoot -notmatch '^[A-Za-z]:\\') { throw 'Invalid scanner provisioning request.' }
$root = [IO.Path]::GetFullPath($policy.dataRoot)
function Assert-PlainDirectory([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    if (-not $item.PSIsContainer) { throw 'Scanner provisioning requires an existing directory.' }
    while ($null -ne $item) {
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Linked scanner provisioning path.' }
        $item = $item.Parent
    }
}
Assert-PlainDirectory $root
$private = Join-Path $root 'Private'
Assert-PlainDirectory $private
$publishedParent = Join-Path $root ('Stores\'+$request.store+'\Scans')
Assert-PlainDirectory $publishedParent
$intakeRoot = Join-Path $private 'Intake'
if (Test-Path -LiteralPath $intakeRoot) { Assert-PlainDirectory $intakeRoot }
$intake = Join-Path $intakeRoot $request.id
$published = Join-Path $publishedParent $request.id
if ((Test-Path -LiteralPath $intake) -or (Test-Path -LiteralPath $published) -or
    (Get-LocalUser -Name $request.account -ErrorAction SilentlyContinue)) { throw 'Scanner account or folder already exists; review the prior setup.' }
$share = Get-SmbShare -Name ScanIntake -ErrorAction SilentlyContinue
if ($share -and [IO.Path]::GetFullPath($share.Path) -ne $intakeRoot) { throw 'ScanIntake points outside the protected intake directory.' }
$registryPath = Join-Path $appRoot 'scanner-accounts.json'
$records = @()
if (Test-Path -LiteralPath $registryPath) {
    $saved = Get-Content -LiteralPath $registryPath -Raw | ConvertFrom-Json
    if ($saved.version -ne 1) { throw 'Invalid scanner account registry.' }
    $records = @($saved.devices)
}
if ($records.Count -ge 1000 -or @($records | Where-Object { $_.id -eq $request.id -or $_.account -eq $request.account }).Count) { throw 'Scanner identity already exists or registry is full.' }
function Save-Registry {
    $staged = $registryPath+'.'+[guid]::NewGuid()+'.tmp'
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes((@{version=1;devices=$records} | ConvertTo-Json -Depth 8))
        $file = [IO.File]::Open($staged,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try { $file.Write($bytes,0,$bytes.Length);$file.Flush($true) } finally { $file.Dispose() }
        if (Test-Path -LiteralPath $registryPath) { [IO.File]::Replace($staged,$registryPath,[System.Management.Automation.Language.NullString]::Value) }
        else { [IO.File]::Move($staged,$registryPath) }
    } finally { if (Test-Path -LiteralPath $staged) { Remove-Item -LiteralPath $staged } }
}
$record = [pscustomobject]@{ id=[string]$request.id;storeId=[string]$request.store;account=[string]$request.account;
    ownerTag=[guid]::NewGuid().ToString('N');phase='provisioning' }
$records += $record
Save-Registry
$created = $false
try {
    $password = ConvertTo-SecureString $request.password -AsPlainText -Force
    try { New-LocalUser -Name $request.account -Password $password -PasswordNeverExpires -UserMayNotChangePassword -Description ('SmartDrive scan '+$record.ownerTag) | Out-Null;$created=$true }
    finally { $password.Dispose() }
    [void][IO.Directory]::CreateDirectory($intakeRoot)
    [void][IO.Directory]::CreateDirectory($intake)
    & icacls.exe $intake /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)M' ($request.account+':(OI)(CI)M') | Out-Null
    if ($LASTEXITCODE) { throw 'Scanner intake permissions failed.' }
    & icacls.exe $intakeRoot /grant ($request.account+':RX') | Out-Null
    if ($LASTEXITCODE) { throw 'Scanner traversal permissions failed.' }
    [void][IO.Directory]::CreateDirectory($published)
    if ($share) { Grant-SmbShareAccess -Name ScanIntake -AccountName $request.account -AccessRight Change -Force | Out-Null }
    else { New-SmbShare -Name ScanIntake -Path $intakeRoot -ChangeAccess $request.account -FolderEnumerationMode AccessBased -CachingMode None | Out-Null }
    $record.phase='active';Save-Registry
} catch {
    if ($created) { Disable-LocalUser -Name $request.account -ErrorAction SilentlyContinue }
    $record.phase='requires-review';Save-Registry
    throw 'Scanner provisioning failed; partial account/folders are retained for review.'
}
