[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Url,
    [switch]$StartLocalService
)
$ErrorActionPreference = 'Stop'
$target = $null
if (-not [uri]::TryCreate($Url, [UriKind]::Absolute, [ref]$target) -or
    $target.Scheme -ne 'https' -or $target.UserInfo -or $target.Fragment) {
    throw 'Supply the configured HTTPS management URL.'
}
if ($StartLocalService) {
    $service = Get-Service -Name SmartDrive -ErrorAction Stop
    if ($service.Status -ne 'Running') {
        Start-Service -Name SmartDrive -ErrorAction Stop
        $service.WaitForStatus([ServiceProcess.ServiceControllerStatus]::Running, [timespan]::FromSeconds(30))
    }
}
# The public route may legitimately redirect to Cloudflare Access. Let the
# browser complete that login instead of treating an unauthenticated HTTP probe
# as an application-health verdict. The console reports dependency health after
# employee sign-in; Running here establishes only the Windows service state.
Start-Process -FilePath $target.AbsoluteUri -ErrorAction Stop
