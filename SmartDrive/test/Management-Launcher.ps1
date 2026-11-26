$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.ServiceProcess
# Exercise the actual launcher with service/browser boundaries replaced. Never
# start a service, open a browser or contact the public route in this fixture.
$global:SmartDriveLauncherEvents = @()
$global:SmartDriveLauncherState = 'Stopped'
$global:SmartDriveLauncherFailWait = $false
function Get-Service {
    param($Name)
    if ($Name -ne 'SmartDrive') { throw 'Unexpected service.' }
    $global:SmartDriveLauncherEvents += 'get'
    $service = [pscustomobject]@{Status=$global:SmartDriveLauncherState}
    $service | Add-Member ScriptMethod WaitForStatus {
        param($Status,$Timeout)
        if ($Status -ne 'Running' -or $Timeout.TotalSeconds -ne 30) { throw 'Unexpected wait.' }
        $global:SmartDriveLauncherEvents += 'wait'
        if ($global:SmartDriveLauncherFailWait) { throw 'Fixture startup timeout.' }
    }
    $service
}
function Start-Service { param($Name); $global:SmartDriveLauncherEvents += 'start' }
function Start-Process { param($FilePath); $global:SmartDriveLauncherEvents += ('open:'+$FilePath) }
function Invoke-WebRequest { throw 'Public login routes must be opened by the browser, not probed.' }
$launcher = Join-Path $PSScriptRoot '..\tools\Open-Management.ps1'
$url = 'https://drive.example/employees/drive/'
function ExpectEvents([string]$Expected) {
    if (($global:SmartDriveLauncherEvents -join ',') -ne $Expected) { throw "Unexpected launcher sequence: $($global:SmartDriveLauncherEvents -join ',')" }
    $global:SmartDriveLauncherEvents = @()
}
& $launcher -Url $url -StartLocalService
ExpectEvents ('get,start,wait,open:'+$url)
$global:SmartDriveLauncherState = 'Running'
& $launcher -Url $url -StartLocalService
ExpectEvents ('get,open:'+$url)
& $launcher -Url $url
ExpectEvents ('open:'+$url)
$global:SmartDriveLauncherState = 'Stopped'; $global:SmartDriveLauncherFailWait = $true
try { & $launcher -Url $url -StartLocalService; throw 'Expected startup failure.' }
catch { if ($_.Exception.Message -notlike '*Fixture startup timeout.*') { throw } }
ExpectEvents 'get,start,wait'
foreach ($invalid in @('http://drive.example/', 'https://user:password@drive.example/', 'https://drive.example/#token')) {
    try { & $launcher -Url $invalid; throw 'Expected URL rejection.' }
    catch { if ($_.Exception.Message -ne 'Supply the configured HTTPS management URL.') { throw } }
    ExpectEvents ''
}
Write-Output 'Management launcher checks passed without starting services, opening browsers or probing login routes.'
Remove-Variable SmartDriveLauncherEvents,SmartDriveLauncherState,SmartDriveLauncherFailWait -Scope Global
