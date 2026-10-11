param(
    [Parameter(Mandatory=$true)][ValidateSet('validate','preview','check-library')][string]$Action,
    [Parameter(Mandatory=$true)][string]$InputPath,
    [string]$OutputPath,
    [string]$StudioPath = (Join-Path $env:LOCALAPPDATA 'Programs\SmartStudio')
)
$ErrorActionPreference = 'Stop'
$badgeJava = Join-Path $StudioPath 'runtime\bin\java.exe'
$badgeApp = Join-Path $StudioPath 'app'
$badgeJars = @(Get-ChildItem -LiteralPath $badgeApp -Filter 'smartstudio-*.jar' -File)
if (!(Test-Path -LiteralPath $badgeJava) -or $badgeJars.Count -ne 1) {
    throw 'Locate the SmartStudio installation with -StudioPath. Install the build containing Badge Cards first.'
}
$badgeArguments = @('-cp', ($badgeJars[0].FullName + ';' + (Join-Path $badgeApp 'lib\*')), 'gy.deckers.studio.BadgeTemplateTool', $Action, $InputPath)
if ($Action -eq 'preview') {
    if (!$OutputPath) { throw 'Preview needs -OutputPath for a new PNG file.' }
    $badgeArguments += $OutputPath
}
& $badgeJava @badgeArguments
if ($LASTEXITCODE -ne 0) { throw 'The badge template check did not pass. Review the message above.' }
