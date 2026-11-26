[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Server = 'DECKERSDRIVE',
    [string]$Share = 'DeckersDrive',
    [Parameter(Mandatory)][pscredential]$Credential,
    [ValidatePattern('^[D-Z]$')][string]$DriveLetter,
    [switch]$ReplaceCredential
)
$ErrorActionPreference = 'Stop'
if ($Server -notmatch '^[A-Za-z0-9][A-Za-z0-9.-]{0,252}$' -or
    $Share -notmatch '^[A-Za-z][A-Za-z0-9_-]{0,39}$') {
    throw 'Use the configured server hostname and share name.'
}
if ($Credential.UserName -notmatch '^[A-Za-z0-9.-]+\\sdpc_[a-z0-9_]{1,12}$') {
    throw 'Use the dedicated server-side sdpc workstation account issued by the administrator.'
}
if ($Credential.UserName.Split('\')[0] -ine $Server.Split('.')[0]) {
    throw 'The credential must belong to the selected drive server.'
}
Add-Type -Path (Join-Path $PSScriptRoot 'WindowsDriveCredential.cs')
if ([WindowsDriveCredential]::Exists($Server) -and -not $ReplaceCredential) {
    throw 'Windows already has a credential for this server. Use ReplaceCredential only to rotate the workstation credential.'
}
if ($DriveLetter -and (Get-PSDrive -Name $DriveLetter -ErrorAction SilentlyContinue)) {
    throw 'The requested drive letter is already in use.'
}
$path = '\\' + $Server + '\' + $Share
if ($PSCmdlet.ShouldProcess($Server, 'Save workstation credential for this Windows profile')) {
    [WindowsDriveCredential]::Save($Server, $Credential.UserName, $Credential.Password)
    if ($DriveLetter) {
        New-PSDrive -Name $DriveLetter -PSProvider FileSystem -Root $path -Persist -Scope Global | Out-Null
    }
    Write-Output "Saved access to $path for the current Windows profile. Open it through File Explorer Network."
}
