[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][ValidatePattern('^sdpc_[a-z0-9_]{1,12}$')][string]$Account
)
$ErrorActionPreference = 'Stop'
$user = Get-LocalUser -Name $Account -ErrorAction Stop
if ($PSCmdlet.ShouldProcess($Account, 'Disable workstation access and close its SMB sessions')) {
    Disable-LocalUser -SID $user.SID
    $identity = $env:COMPUTERNAME + '\' + $Account
    Get-SmbSession | Where-Object ClientUserName -EQ $identity | ForEach-Object {
        Close-SmbSession -SessionId $_.SessionId -Force -ErrorAction Stop
    }
    Write-Output 'Workstation access disabled and its active SMB sessions closed.'
}
