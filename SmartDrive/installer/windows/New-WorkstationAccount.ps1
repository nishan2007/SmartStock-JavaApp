[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][ValidatePattern('^sdpc_[a-z0-9_]{1,12}$')][string]$Account,
    [Parameter(Mandatory)][ValidatePattern('^[1-9][0-9]{0,8}$')][string[]]$StoreIds,
    [Parameter(Mandatory)][securestring]$Password
)
$ErrorActionPreference = 'Stop'
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Create workstation accounts from an administrator session in the guest.'
}
if (Get-LocalUser -Name $Account -ErrorAction SilentlyContinue) { throw 'The workstation account already exists.' }
foreach ($id in $StoreIds) { $null = Get-LocalGroup -Name ('SDStore' + $id) -ErrorAction Stop }
if ($PSCmdlet.ShouldProcess($Account, 'Create a workstation identity and grant assigned store access')) {
    New-LocalUser -Name $Account -Password $Password -UserMayNotChangePassword -PasswordNeverExpires -Description 'SmartDrive workstation access' | Out-Null
    try {
        foreach ($id in $StoreIds) {
            Add-LocalGroupMember -Group ('SDStore' + $id) -Member $Account -ErrorAction Stop
        }
    } catch {
        Disable-LocalUser -Name $Account
        throw
    }
    Write-Output 'Workstation account created. Configure its credential in the shared shop Windows profile.'
}
