param([Parameter(Mandatory=$true)][string]$AppDirectory,[switch]$Remove)
$ErrorActionPreference='Stop'
$TaskName='SmartStockDisplayUpdates'
$AppDirectory=[IO.Path]::GetFullPath($AppDirectory).TrimEnd('\')
$Root=Join-Path $env:ProgramData 'SmartStockDisplayUpdates'
if($Remove){
    $configPath=Join-Path $Root 'installation.json'
    if(Test-Path -LiteralPath $configPath){
        $installed=Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
        if([IO.Path]::GetFullPath($installed.appDirectory).TrimEnd('\') -eq $AppDirectory){
            Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
        }
    }
    exit
}
$Allowed=[Environment]::GetFolderPath('ProgramFiles').TrimEnd('\')+'\'
if(-not $AppDirectory.StartsWith($Allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'The update service requires a protected Program Files installation.'}
$parent=Get-Item -LiteralPath $AppDirectory
while($parent){if($parent.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Reparse points are not allowed.'};$parent=$parent.Parent}
foreach($child in Get-ChildItem -LiteralPath $AppDirectory -Recurse -Force){
    if($child.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Unsafe installation content.'}
}
# Elevated interactive installs can otherwise leave an individual administrator
# as owner, allowing that account to grant write access without elevation.
& icacls.exe $AppDirectory /setowner '*S-1-5-32-544' /T /Q | Out-Null
if($LASTEXITCODE -ne 0){throw 'Could not protect installation ownership.'}
# Never adopt pre-existing user-controlled directories as privileged code.
if(Test-Path -LiteralPath $Root){
    $item=Get-Item -LiteralPath $Root
    $sid=(Get-Acl -LiteralPath $Root).GetOwner([Security.Principal.SecurityIdentifier]).Value
    if(($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $sid -notin @('S-1-5-18','S-1-5-32-544')){throw 'Unsafe update service directory.'}
    # Compare raw SIDs, including accounts Windows cannot currently resolve.
    foreach($rule in (Get-Acl -LiteralPath $Root).GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])){
        $identity=$rule.IdentityReference.Value
        $writes=[Security.AccessControl.FileSystemRights]::Write -bor [Security.AccessControl.FileSystemRights]::Delete -bor [Security.AccessControl.FileSystemRights]::ChangePermissions -bor [Security.AccessControl.FileSystemRights]::TakeOwnership
        if($rule.AccessControlType -eq 'Allow' -and ($rule.FileSystemRights -band $writes) -and $identity -notin @('S-1-5-18','S-1-5-32-544')){throw 'Update directory is writable by an untrusted principal.'}
    }
}else{
    New-Item -ItemType Directory -Path $Root | Out-Null
}
$Acl=New-Object Security.AccessControl.DirectorySecurity
$Acl.SetOwner((New-Object Security.Principal.SecurityIdentifier('S-1-5-32-544')))
$Acl.SetAccessRuleProtection($true,$false)
foreach($entry in @(@('S-1-5-18','FullControl'),@('S-1-5-32-544','FullControl'),@('S-1-5-32-545','ReadAndExecute'))){
    $Acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule((New-Object Security.Principal.SecurityIdentifier($entry[0])),$entry[1],'ContainerInherit,ObjectInherit','None','Allow')))
}
Set-Acl -LiteralPath $Root -AclObject $Acl
foreach($child in Get-ChildItem -LiteralPath $Root -Force){
    if($child.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Unsafe update service content.'}
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'run-display-update-service.ps1') -Destination (Join-Path $Root 'service.ps1') -Force
@{appDirectory=$AppDirectory} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Root 'installation.json') -Encoding UTF8
$PowerShell=Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'
$Action=New-ScheduledTaskAction -Execute $PowerShell -Argument ('-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+(Join-Path $Root 'service.ps1')+'"')
$Principal=New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$Settings=New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Hours 1) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName $TaskName -Action $Action -Principal $Principal -Settings $Settings -Force | Out-Null
$Scheduler=New-Object -ComObject 'Schedule.Service';$Scheduler.Connect()
$Task=$Scheduler.GetFolder('\').GetTask($TaskName)
# Users may run this fixed action, never edit the command or principal.
$Task.SetSecurityDescriptor('D:P(A;;GA;;;SY)(A;;GA;;;BA)(A;;GRGX;;;AU)',0)
