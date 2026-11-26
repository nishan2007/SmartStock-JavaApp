$ErrorActionPreference = 'Stop'
# Exercise migration policy with synthetic Hyper-V/storage responses only.
# No VHDX, VM, volume or physical disk is created or changed.
$global:SmartDrivePolicyFixture = @{capacity=4000000000000L;health='Healthy'}
function Get-VM { param($Name) [pscustomobject]@{ State='Off' } }
function Get-VMSnapshot { param($VM) }
function Get-VMHardDiskDrive { param($VM) [pscustomobject]@{Path='C:\smartdrive-policy-source.vhdx'} }
function Get-VHD { param($Path) [pscustomobject]@{Size=300000000000L;ParentPath=$null;Attached=$false} }
function Test-VHD { param($Path) $true }
function Get-Volume { param($DriveLetter) [pscustomobject]@{FileSystem='NTFS';SizeRemaining=$global:SmartDrivePolicyFixture.capacity} }
function Get-Partition { param($DriveLetter) [pscustomobject]@{Number=9} }
function Get-Disk { param([Parameter(ValueFromPipeline)]$InputObject) process { [pscustomobject]@{Number=9} } }
function Get-VirtualDisk { [pscustomobject]@{ResiliencySettingName='Mirror';HealthStatus=$global:SmartDrivePolicyFixture.health;NumberOfDataCopies=2} }
function Resize-VHD { throw 'Dry run must never resize a disk.' }
function Set-VMHardDiskDrive { throw 'Dry run must never switch a disk.' }
$tool = Join-Path $PSScriptRoot '..\tools\Manage-Storage.ps1'
$arguments = @{Action='Migrate';PilotPath='C:\smartdrive-policy-source.vhdx';MirrorPath='D:\smartdrive-policy-destination.vhdx';WhatIf=$true}
if ((Test-Path -LiteralPath $arguments.PilotPath) -or (Test-Path -LiteralPath $arguments.MirrorPath)) {
    throw 'Policy fixture paths must not exist.'
}
function Reject([scriptblock]$Action,[string]$Message) {
    try { & $Action } catch { if ($_.Exception.Message -like "*$Message*") { return }; throw }
    throw "Expected rejection: $Message"
}
& $tool @arguments -ExpandToBytes 3500000000000L
Reject { & $tool @arguments -ExpandToBytes 200000000000L } 'cannot shrink'
$global:SmartDrivePolicyFixture.capacity = 1000000000000L
Reject { & $tool @arguments -ExpandToBytes 3500000000000L } 'complete target'
$global:SmartDrivePolicyFixture.capacity = 4000000000000L
$global:SmartDrivePolicyFixture.health = 'Degraded'
Reject { & $tool @arguments -ExpandToBytes 3500000000000L } 'healthy Windows Storage Spaces mirror'
Reject { & $tool -Action Plan -ExpandToBytes 3500000000000L } 'only supported'
Write-Output 'Storage migration policy checks passed without changing disks or VMs.'
Remove-Variable SmartDrivePolicyFixture -Scope Global
