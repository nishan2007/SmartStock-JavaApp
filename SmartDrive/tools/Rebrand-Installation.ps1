[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][string]$ConfigPath,
    [Parameter(Mandatory)][string]$InstallationName,
    [string]$DisplayName,
    [string]$InstallRoot = 'C:\Program Files\SmartDrive',
    [string]$JournalRoot,
    [string]$NodePath = 'node.exe',
    [switch]$PlanOnly,
    [switch]$MaintenanceWindow
)
$ErrorActionPreference = 'Stop'
function Assert-PlainPath([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    while ($null -ne $item) {
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Linked rebranding path.' }
        $item = if ($item.PSIsContainer) { $item.Parent } else { $item.Directory }
    }
}
function Save-Json([string]$Path,$Value) {
    $staged=$Path+'.'+[guid]::NewGuid()+'.tmp'
    try {
        $bytes=[Text.Encoding]::UTF8.GetBytes(($Value|ConvertTo-Json -Depth 100))
        $file=[IO.File]::Open($staged,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try {$file.Write($bytes,0,$bytes.Length);$file.Flush($true)} finally {$file.Dispose()}
        if(Test-Path -LiteralPath $Path){[IO.File]::Replace($staged,$Path,[System.Management.Automation.Language.NullString]::Value)}
        else{[IO.File]::Move($staged,$Path)}
    } finally {if(Test-Path -LiteralPath $staged){Remove-Item -LiteralPath $staged}}
}
function Within([string]$Root,[string]$Path) {
    $Root=[IO.Path]::GetFullPath($Root).TrimEnd('\');$Path=[IO.Path]::GetFullPath($Path)
    $Path -eq $Root -or $Path.StartsWith($Root+'\',[StringComparison]::OrdinalIgnoreCase)
}
if(-not $DisplayName){$DisplayName=$InstallationName}
$configHash=(Get-FileHash -LiteralPath $ConfigPath -Algorithm SHA256).Hash
$raw=& $NodePath (Join-Path $PSScriptRoot 'rebrand-plan.mjs') $ConfigPath $InstallationName $DisplayName
if($LASTEXITCODE){throw 'Could not validate the rebranding plan.'}
$plan=($raw -join [Environment]::NewLine)|ConvertFrom-Json
if($PlanOnly){
    [pscustomobject]@{From=$plan.before.installationName;To=$plan.after.installationName;DisplayName=$plan.after.displayName;
        DisplayOnly=$plan.displayOnly;Changes=$plan.changes;Workstation=$plan.workstation;Retained=$plan.retained}
    return
}
if(-not $MaintenanceWindow){throw 'Freeze employee file access and stop both SmartDrive services, then specify MaintenanceWindow.'}
if(-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){
    throw 'Run rebranding as an administrator inside the installed SmartDrive guest.'
}
$old=[IO.Path]::GetFullPath($plan.before.dataRoot);$new=[IO.Path]::GetFullPath($plan.after.dataRoot)
$ConfigPath=(Resolve-Path -LiteralPath $ConfigPath).Path
$policyPath=Join-Path $InstallRoot 'account-stores.json'
Assert-PlainPath $ConfigPath;Assert-PlainPath $policyPath;Assert-PlainPath $old
$serviceSettingsPath=Join-Path $InstallRoot 'service.json'
Assert-PlainPath $serviceSettingsPath
$serviceSettings=Get-Content -LiteralPath $serviceSettingsPath -Raw|ConvertFrom-Json
if([IO.Path]::GetFullPath($serviceSettings.configPath) -ne $ConfigPath -or [IO.Path]::GetFullPath($serviceSettings.appRoot) -ne [IO.Path]::GetFullPath($InstallRoot)){
    throw 'Use the configuration and directory referenced by the installed service.'
}
if((Within $old $ConfigPath) -or (Within $new $ConfigPath) -or (Within $old $InstallRoot) -or (Within $new $InstallRoot)){
    throw 'Keep installed service binaries and configuration outside the renamed data tree.'
}
$policy=Get-Content -LiteralPath $policyPath -Raw|ConvertFrom-Json
if([IO.Path]::GetFullPath($policy.dataRoot) -ne $old){throw 'Installed account policy does not match the current data root.'}
foreach($name in @('SmartDrive','SmartDriveAccounts')){
    if((Get-Service -Name $name -ErrorAction Stop).Status -ne 'Stopped'){throw 'Stop both SmartDrive services before rebranding.'}
}
if(-not $JournalRoot -or $JournalRoot -notmatch '^[A-Za-z]:\\'){throw 'Choose a new absolute private journal directory outside the data tree.'}
$JournalRoot=[IO.Path]::GetFullPath($JournalRoot)
if((Within $old $JournalRoot) -or (Within $new $JournalRoot) -or (Test-Path -LiteralPath $JournalRoot)){throw 'Use a new journal directory outside both data roots.'}
Assert-PlainPath ([IO.Path]::GetDirectoryName($JournalRoot))
$shares=@()
$updatedRegistry=$null
if(-not $plan.displayOnly -and $plan.config.scanStateRoot){
    $scanChange=$plan.changes|Where-Object field -eq 'scanStateRoot'
    $oldRegistryPath=Join-Path ([string]$scanChange.before) 'enrolled-scanners.json'
    if(Test-Path -LiteralPath $oldRegistryPath){
        Assert-PlainPath $oldRegistryPath
        $updatedRegistry=Get-Content -LiteralPath $oldRegistryPath -Raw|ConvertFrom-Json
        if($updatedRegistry.version -ne 1 -or $null -eq $updatedRegistry.records){throw 'Invalid scanner registry during rebranding.'}
        foreach($device in $updatedRegistry.records){
            if($device.id -notmatch '^[A-Za-z0-9][A-Za-z0-9_-]{0,47}$'){throw 'Invalid scanner ID in registry.'}
            if($device.PSObject.Properties['intake']){$device.intake=Join-Path $new ('Private\Intake\'+$device.id)}
        }
    }
}
if(-not $plan.displayOnly){
    if(Test-Path -LiteralPath $new){throw 'The new data root already exists.'}
    if([IO.Path]::GetDirectoryName($old) -ne [IO.Path]::GetDirectoryName($new)){throw 'Rebranding must be a same-parent directory rename.'}
    if(Get-SmbShare -Name $plan.after.shareName -ErrorAction SilentlyContinue){throw 'The new share name already exists.'}
    $staff=Get-SmbShare -Name $plan.before.shareName -ErrorAction Stop
    if([IO.Path]::GetFullPath($staff.Path) -ne [IO.Path]::GetFullPath($plan.before.filesRoot)){throw 'Current staff share points outside the expected Stores folder.'}
    $allowed=@($plan.before.shareName,'ScanIntake')
    foreach($share in @(Get-SmbShare|Where-Object {$_.Path -and (Within $old $_.Path)})){
        if($share.Name -notin $allowed){throw 'An additional share uses the data tree. Review its migration separately.'}
        if($share.Name -eq 'ScanIntake' -and [IO.Path]::GetFullPath($share.Path) -ne (Join-Path $old 'Private\Intake')){throw 'Unexpected scanner share path.'}
        $shares+=@{Name=$share.Name;Path=$share.Path;Description=$share.Description;EncryptData=[bool]$share.EncryptData;
            FolderEnumerationMode=[string]$share.FolderEnumerationMode;CachingMode=[string]$share.CachingMode;
            Access=@(Get-SmbShareAccess -Name $share.Name|Select-Object AccountName,@{n='Type';e={[string]$_.AccessControlType}},@{n='Right';e={[string]$_.AccessRight}})}
    }
    if(@(Get-SmbOpenFile|Where-Object {$_.Path -and (Within $old $_.Path)}).Count){throw 'Staff or copier files remain open over SMB. Close them before continuing.'}
    # Inspect links without following them; never recursively move an unchecked path.
    $pending=New-Object 'Collections.Generic.Stack[string]';$pending.Push($old)
    while($pending.Count){foreach($item in Get-ChildItem -LiteralPath $pending.Pop() -Force){
        if($item.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'The data tree contains a link; review it before rebranding.'}
        if($item.PSIsContainer){$pending.Push($item.FullName)}
    }}
}
if($PSCmdlet.ShouldProcess($old,'Rename company storage and shares, retain an old-share alias, and update installed configuration')){
    if((Get-FileHash -LiteralPath $ConfigPath -Algorithm SHA256).Hash -ne $configHash){throw 'Configuration changed during preflight. Generate and review a fresh plan.'}
    [void][IO.Directory]::CreateDirectory($JournalRoot)
    & icacls.exe $JournalRoot /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' | Out-Null
    if($LASTEXITCODE){throw 'Could not protect the rebranding journal.'}
    Copy-Item -LiteralPath $ConfigPath -Destination (Join-Path $JournalRoot 'installation.before.json')
    Copy-Item -LiteralPath $policyPath -Destination (Join-Path $JournalRoot 'account-stores.before.json')
    $journal=@{Version=1;Phase='prepared';Plan=$plan;Shares=$shares}
    $journalPath=Join-Path $JournalRoot 'rebrand.json';Save-Json $journalPath $journal
    try{
        if(-not $plan.displayOnly){
            $journal.Phase='removing-shares';Save-Json $journalPath $journal
            foreach($share in $shares){Remove-SmbShare -Name $share.Name -Force -Confirm:$false}
            Rename-Item -LiteralPath $old -NewName $plan.after.installationName
            $journal.Phase='data-renamed';Save-Json $journalPath $journal
            $administrator=(Get-LocalGroup -SID 'S-1-5-32-544').Name
            foreach($share in $shares){
                $target=if($share.Name -eq 'ScanIntake'){Join-Path $new 'Private\Intake'}else{$plan.after.filesRoot}
                $names=if($share.Name -eq $plan.before.shareName){@($plan.after.shareName,$plan.before.shareName)}else{@($share.Name)}
                foreach($name in $names){
                    New-SmbShare -Name $name -Path $target -FullAccess $administrator -Description $share.Description -EncryptData $share.EncryptData -FolderEnumerationMode $share.FolderEnumerationMode -CachingMode $share.CachingMode|Out-Null
                    foreach($entry in $share.Access){
                        if($entry.Type -eq 'Deny'){Block-SmbShareAccess -Name $name -AccountName $entry.AccountName -Force|Out-Null}
                        else{Grant-SmbShareAccess -Name $name -AccountName $entry.AccountName -AccessRight $entry.Right -Force|Out-Null}
                    }
                    foreach($entry in @(Get-SmbShareAccess -Name $name)){
                        if(-not @($share.Access|Where-Object {$_.AccountName -eq $entry.AccountName -and $_.Type -eq [string]$entry.AccessControlType}).Count){
                            Revoke-SmbShareAccess -Name $name -AccountName $entry.AccountName -Force|Out-Null
                        }
                    }
                    $expectedAccess=@($share.Access|ForEach-Object {($_.AccountName+'/'+$_.Type+'/'+$_.Right).ToUpperInvariant()}|Sort-Object)
                    $actualAccess=@(Get-SmbShareAccess -Name $name|ForEach-Object {($_.AccountName+'/'+[string]$_.AccessControlType+'/'+[string]$_.AccessRight).ToUpperInvariant()}|Sort-Object)
                    if(($expectedAccess -join "`n") -cne ($actualAccess -join "`n")){throw 'Recreated share permissions did not match the original.'}
                }
            }
            $policy.dataRoot=$new;Save-Json $policyPath $policy
            if($null -ne $updatedRegistry){
                $registryPath=Join-Path $plan.config.scanStateRoot 'enrolled-scanners.json'
                Save-Json $registryPath $updatedRegistry
            }
        }
        Save-Json $ConfigPath $plan.config
        Set-Service -Name SmartDrive -DisplayName ($plan.after.displayName+' (SmartDrive)')
        Save-Json (Join-Path $JournalRoot 'workstation-branding.json') @{server=$plan.serverName;oldShare=$plan.before.shareName;newShare=$plan.after.shareName;
            displayName=$plan.after.displayName;url=([string]$plan.publicOrigin).TrimEnd('/')+'/employees/drive/'}
        $journal.Phase='configured-services-stopped';Save-Json $journalPath $journal
        Write-Output 'Rebranding applied. Services remain stopped for verification. The old staff share remains as a compatibility alias.'
        Write-Output 'Update workstation mappings and management shortcuts, then verify access, previews, scans and backup/restore before retiring the alias.'
    }catch{
        $journal.Phase='requires-review';Save-Json $journalPath $journal
        throw 'Rebranding stopped after a failure. Keep services stopped and inspect the private journal, saved configuration and actual share/data paths. No automatic rollback was attempted.'
    }
}
