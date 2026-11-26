$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '..\tools\VM-Provisioning.ps1')
$global:SmartDriveVMFixture=@{calls=(New-Object 'Collections.Generic.List[string]');failTpm=$false;enabled=$true}
function New-VM {param($Name,$Generation,$MemoryStartupBytes,$Path,$NewVHDPath,$NewVHDSizeBytes,$SwitchName)
    if($Generation -ne 2 -or $MemoryStartupBytes -ne 4GB -or $NewVHDSizeBytes -ne 80GB){throw 'Unexpected guest sizing.'}
    $global:SmartDriveVMFixture.calls.Add('new')
}
function Set-VM {param($Name,$AutomaticStartAction,$AutomaticStopAction,$ErrorAction) $global:SmartDriveVMFixture.calls.Add('startup-'+$AutomaticStartAction)}
function Set-VMProcessor {param($VMName,$Count) if($Count -ne 2){throw 'Unexpected CPU count.'}}
function Set-VMFirmware {param($VMName,$EnableSecureBoot,$SecureBootTemplate,$FirstBootDevice)
    if($EnableSecureBoot){if($EnableSecureBoot -ne 'On' -or $SecureBootTemplate -ne 'MicrosoftWindows'){throw 'Unexpected firmware policy.'};$global:SmartDriveVMFixture.calls.Add('secure-boot')}
}
function Set-VMKeyProtector {param($VMName,[switch]$NewLocalKeyProtector)
    if(-not $NewLocalKeyProtector){throw 'Local protector not requested.'};$global:SmartDriveVMFixture.calls.Add('protector')
}
function Enable-VMTPM {param($VMName) $global:SmartDriveVMFixture.calls.Add('tpm');if($global:SmartDriveVMFixture.failTpm){throw 'Synthetic TPM failure'}}
function Get-VMSecurity {param($VMName) [pscustomobject]@{TpmEnabled=$global:SmartDriveVMFixture.enabled}}
function Get-VMFirmware {param($VMName) [pscustomobject]@{SecureBoot='On'}}
function Add-VMHardDiskDrive {param($VMName,$Path) $global:SmartDriveVMFixture.calls.Add('data')}
function Add-VMDvdDrive {param($VMName,$Path,[switch]$PassThru) $global:SmartDriveVMFixture.calls.Add('iso');[pscustomobject]@{Name='fixture-dvd'}}
function Start-VM {throw 'Provisioning must not boot the VM.'}
try{
    Initialize-NewDriveVM Fixture 'D:\Fixture' 'D:\Fixture\Windows.vhdx' 'D:\pilot.vhdx' 'D:\fixture.iso' FixtureSwitch
    if(($global:SmartDriveVMFixture.calls -join ',') -ne 'new,startup-Nothing,secure-boot,protector,tpm,data,iso,startup-Start'){throw 'VM security/activation sequence changed.'}
    foreach($failure in @('command','verification')){
        $global:SmartDriveVMFixture.calls.Clear();$global:SmartDriveVMFixture.failTpm=$failure -eq 'command';$global:SmartDriveVMFixture.enabled=$false
        $rejected=$false;try{Initialize-NewDriveVM Fixture 'D:\Fixture' 'D:\Fixture\Windows.vhdx' 'D:\pilot.vhdx' 'D:\fixture.iso' FixtureSwitch}catch{$rejected=$true}
        if(-not $rejected -or $global:SmartDriveVMFixture.calls.Contains('startup-Start') -or $global:SmartDriveVMFixture.calls.Contains('data')){throw 'An incomplete security setup enabled boot or attached data.'}
    }
    Write-Output 'VM provisioning policy passed: Gen2 sizing, Secure Boot, TPM order and incomplete-setup safeguards. No VMs or disks changed.'
}finally{Remove-Variable SmartDriveVMFixture -Scope Global}
