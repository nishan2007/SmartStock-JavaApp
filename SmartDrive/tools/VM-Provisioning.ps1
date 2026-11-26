# Command sequence isolated for fixture verification; entry point performs host
# permission, path, capacity, ISO and switch preflight before invoking it.
function Initialize-NewDriveVM([string]$Name,[string]$Root,[string]$SystemDisk,[string]$DataDisk,[string]$WindowsIso,[string]$SwitchName) {
    $created=$false
    try{
        $null=New-VM -Name $Name -Generation 2 -MemoryStartupBytes 4GB -Path $Root -NewVHDPath $SystemDisk -NewVHDSizeBytes 80GB -SwitchName $SwitchName
        $created=$true
        Set-VM -Name $Name -AutomaticStartAction Nothing -AutomaticStopAction ShutDown
        Set-VMProcessor -VMName $Name -Count 2
        Set-VMFirmware -VMName $Name -EnableSecureBoot On -SecureBootTemplate MicrosoftWindows
        # Only called for a newly created VM, never to replace an existing TPM.
        Set-VMKeyProtector -VMName $Name -NewLocalKeyProtector
        Enable-VMTPM -VMName $Name
        if(-not (Get-VMSecurity -VMName $Name).TpmEnabled -or [string](Get-VMFirmware -VMName $Name).SecureBoot -ne 'On'){
            throw 'Virtual TPM and Secure Boot verification failed.'
        }
        Add-VMHardDiskDrive -VMName $Name -Path $DataDisk
        $dvd=Add-VMDvdDrive -VMName $Name -Path $WindowsIso -PassThru
        Set-VMFirmware -VMName $Name -FirstBootDevice $dvd
        Set-VM -Name $Name -AutomaticStartAction Start -AutomaticStopAction ShutDown
    }catch{
        if($created){
            try{Set-VM -Name $Name -AutomaticStartAction Nothing -AutomaticStopAction ShutDown -ErrorAction Stop}
            catch{throw 'VM setup failed and automatic-start disabling could not be confirmed. Keep the VM off and inspect Hyper-V Manager and retained disks before retrying.'}
        }
        throw 'VM provisioning did not complete. The new VM/disks are retained for review. Inspect Hyper-V Manager, automatic-start policy and security settings before retrying.'
    }
}
