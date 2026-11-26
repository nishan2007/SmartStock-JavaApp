[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ConfigPath,
    [Parameter(Mandatory)][string]$RecoveryRoot,
    [string]$StudioPath = (Join-Path $env:LOCALAPPDATA 'Programs\SmartStudio'),
    [string]$NodePath = 'node.exe',
    [switch]$MaintenanceWindow
)
$ErrorActionPreference = 'Stop'
if (-not $MaintenanceWindow) { throw 'Freeze employee file access and stop SmartDrive, then specify -MaintenanceWindow.' }
$service = Get-Service -Name SmartDrive -ErrorAction SilentlyContinue
if ($service -and $service.Status -ne 'Stopped') { throw 'Stop the SmartDrive service before preparing legacy previews.' }
$java = Join-Path $StudioPath 'runtime\bin\java.exe'
$app = Join-Path $StudioPath 'app'
$jars = @(Get-ChildItem -LiteralPath $app -Filter 'smartstudio-*.jar' -File)
if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or $jars.Count -ne 1) {
    throw 'Supply the SmartStudio installation containing the preview preparation utility with -StudioPath.'
}
$classpath = $jars[0].FullName + ';' + (Join-Path $app 'lib\*')
& $NodePath (Join-Path $PSScriptRoot 'prepare-previews.mjs') $ConfigPath $java $classpath $RecoveryRoot '--maintenance-window'
if ($LASTEXITCODE -ne 0) { throw 'Preview preparation failed. Keep writes frozen and review the recovery journal.' }
