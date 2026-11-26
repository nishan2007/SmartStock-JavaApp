[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Url,
    [string]$DisplayName = 'SmartDrive',
    [string]$ConfigPath,
    [switch]$Guest
)
$ErrorActionPreference = 'Stop'
$shortcutName = $DisplayName
if ($ConfigPath) {
    if ($PSBoundParameters.ContainsKey('Url') -or $PSBoundParameters.ContainsKey('DisplayName')) { throw 'Use either ConfigPath or explicit URL/display name options.' }
    $config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
    $shortcutName = [string]$config.installationName
    if ($shortcutName -notmatch '^[A-Za-z][A-Za-z0-9_-]{0,39}$' -or $shortcutName -match '^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$') { throw 'Invalid installation name.' }
    $DisplayName = if ($config.displayName) { [string]$config.displayName } else { $shortcutName }
    if (-not $DisplayName.Trim() -or $DisplayName.Length -gt 80 -or $DisplayName -match '[\x00-\x1f\x7f]') { throw 'Invalid display name.' }
    $Url = ([string]$config.publicOrigin).TrimEnd('/') + '/employees/drive/'
} elseif ($DisplayName -notmatch '^[A-Za-z][A-Za-z0-9 _-]{0,59}$') {
    throw 'Use a display name containing letters, numbers, spaces, underscores or hyphens.'
}
$target = $null
if (-not [uri]::TryCreate($Url, [UriKind]::Absolute, [ref]$target) -or
    $target.Scheme -ne 'https' -or $target.UserInfo -or $Url -match '["\r\n]' -or $target.Fragment) {
    throw 'Supply the configured HTTPS management URL.'
}
$launcher = Join-Path $PSScriptRoot 'Open-Management.ps1'
$desktop = [Environment]::GetFolderPath('Desktop')
$shortcutPath = Join-Path $desktop ($shortcutName + ' Management.lnk')
if (Test-Path -LiteralPath $shortcutPath) {
    throw 'The management shortcut already exists. Remove or rename it before reinstalling.'
}
if ($PSCmdlet.ShouldProcess($shortcutPath, 'Create management desktop shortcut')) {
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $shortcut.Arguments = '-NoProfile -File "' + $launcher + '" -Url "' + $target.AbsoluteUri + '"'
    if ($Guest) { $shortcut.Arguments += ' -StartLocalService' }
    $shortcut.WorkingDirectory = $PSScriptRoot
    $shortcut.Description = $DisplayName + ' management'
    $shortcut.Save()
}
