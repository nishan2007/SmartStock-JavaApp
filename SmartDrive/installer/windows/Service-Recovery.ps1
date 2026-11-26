function Set-SmartDriveServiceRecovery {
    # Called only within an approved install/maintenance mutation. Keep both
    # services stopped; this changes SCM recovery policy, not service state.
    foreach ($serviceName in @('SmartDriveAccounts', 'SmartDrive')) {
        & sc.exe failure $serviceName reset= 86400 actions= restart/10000/restart/30000/restart/60000 | Out-Null
        if ($LASTEXITCODE) { throw "Could not configure recovery for $serviceName. Services remain stopped; review SCM recovery settings before starting." }
    }
}
