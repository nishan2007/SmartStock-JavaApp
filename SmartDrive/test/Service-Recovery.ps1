$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\installer\windows\Service-Recovery.ps1')
$global:SmartDriveRecoveryFixture = @{ calls=@(); fail=$null }
function sc.exe {
    $global:SmartDriveRecoveryFixture.calls += ($args -join ' ')
    $global:LASTEXITCODE = if ($args[1] -eq $global:SmartDriveRecoveryFixture.fail) { 5 } else { 0 }
}
try {
    Set-SmartDriveServiceRecovery
    $expected = @('failure SmartDriveAccounts reset= 86400 actions= restart/10000/restart/30000/restart/60000',
        'failure SmartDrive reset= 86400 actions= restart/10000/restart/30000/restart/60000')
    if (($global:SmartDriveRecoveryFixture.calls -join '|') -ne ($expected -join '|')) { throw 'Unexpected recovery configuration.' }
    foreach ($failed in @('SmartDriveAccounts','SmartDrive')) {
        $global:SmartDriveRecoveryFixture.calls=@();$global:SmartDriveRecoveryFixture.fail=$failed
        try { Set-SmartDriveServiceRecovery; throw 'Expected recovery failure.' }
        catch { if ($_.Exception.Message -notlike "Could not configure recovery for $failed.*") { throw } }
        $count=if($failed -eq 'SmartDriveAccounts'){1}else{2}
        if($global:SmartDriveRecoveryFixture.calls.Count -ne $count){throw 'Recovery continued after a failed command.'}
    }
    Write-Output 'Service recovery configuration and failure checks passed with mocked SCM commands.'
} finally { Remove-Variable SmartDriveRecoveryFixture -Scope Global }
