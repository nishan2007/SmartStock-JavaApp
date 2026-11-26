[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Name = 'DeckersDrive',
    [string]$Root = 'D:\SmartDrive\VM',
    [Parameter(Mandatory)][string]$SwitchName,
    [Parameter(Mandatory)][string]$WindowsIso,
    [string]$DataDisk = 'D:\SmartDrive\DeckersDrive-data.vhdx'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'VM-Provisioning.ps1')
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run VM setup from an administrator PowerShell window.'
}
if ($Name -notmatch '^[A-Za-z][A-Za-z0-9-]{0,39}$' -or $Root -notmatch '^[A-Za-z]:\\') {
    throw 'Supply a safe VM name and absolute local VM directory.'
}
$Root = [IO.Path]::GetFullPath($Root)
foreach($command in @('New-VM','Set-VM','Set-VMProcessor','Set-VMFirmware','Set-VMKeyProtector','Enable-VMTPM','Get-VMSecurity','Get-VMFirmware','Add-VMHardDiskDrive','Add-VMDvdDrive')){
    if(-not (Get-Command $command -ErrorAction SilentlyContinue)){throw 'Install Hyper-V management tools with virtual TPM support before provisioning.'}
}
if (Get-VM -Name $Name -ErrorAction SilentlyContinue) {
    throw 'The VM already exists. Review it in Hyper-V Manager before continuing.'
}
$network = Get-VMSwitch -Name $SwitchName -ErrorAction Stop
if([string]$network.SwitchType -ne 'External' -or -not @(Get-VMNetworkAdapter -ManagementOS|Where-Object SwitchName -EQ $SwitchName).Count){
    throw 'Choose an existing external Ethernet switch that retains a host management adapter.'
}
if($DataDisk -notmatch '^[A-Za-z]:\\' -or [IO.Path]::GetExtension($DataDisk) -ne '.vhdx'){
    throw 'Supply the absolute local pilot data VHDX path.'
}
if ([IO.Path]::GetExtension($WindowsIso) -ne '.iso' -or
    -not (Test-Path -LiteralPath $WindowsIso -PathType Leaf)) {
    throw 'Supply an existing Windows installation ISO.'
}
$disk = Get-VHD -Path $DataDisk
if ($disk.Attached -or $disk.ParentPath -or -not (Test-VHD -Path $DataDisk)) {
    throw 'The data disk must be a valid standalone VHDX with no active mounts.'
}
$systemDisk = Join-Path $Root 'Windows.vhdx'
if (Test-Path -LiteralPath $Root) {
    throw 'Choose a new VM directory to preserve any existing files.'
}
$volume = Get-Volume -DriveLetter $Root[0]
if ($volume.FileSystem -notin 'NTFS', 'ReFS' -or $volume.SizeRemaining -lt 85GB) {
    throw 'The VM volume needs NTFS or ReFS and at least 85 GiB free.'
}
if ($PSCmdlet.ShouldProcess($Name, 'Create Generation 2 Windows VM with 4 GiB RAM and existing data disk')) {
    Initialize-NewDriveVM $Name $Root $systemDisk $DataDisk (Resolve-Path -LiteralPath $WindowsIso).Path $SwitchName
    Write-Output 'VM created with Secure Boot and virtual TPM, and left powered off. Install licensed Windows, then configure the guest hostname and network.'
    Write-Output 'Retain protected VM/key-protector recovery material and any guest BitLocker recovery keys with independent recovery records, outside Git.'
}
