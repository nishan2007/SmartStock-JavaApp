[CmdletBinding(SupportsShouldProcess)]
param([Parameter(Mandatory)][string]$ConfigPath,[string]$NodePath=(Get-Command node.exe -ErrorAction Stop).Source)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Origin-TlsPolicy.ps1')
if(-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){
    throw 'Configure origin TLS as an administrator inside the SmartDrive guest.'
}
$service=Get-Service -Name SmartDrive -ErrorAction SilentlyContinue
if($service -and $service.Status -ne 'Stopped'){throw 'Stop SmartDrive before changing origin certificate permissions or firewall policy.'}
$NodePath=(Resolve-Path -LiteralPath $NodePath).Path
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$raw=& $NodePath (Join-Path $root 'tools\validate-origin.mjs') $ConfigPath
if($LASTEXITCODE){throw 'Origin TLS validation failed; no certificate permissions or firewall rules changed.'}
$origin=($raw -join [Environment]::NewLine)|ConvertFrom-Json
if(-not $origin.tls){throw 'Configure originTls before running this tool. Loopback-only HTTP requires no inbound origin rule.'}
$existing=@(Get-NetFirewallRule -Name SmartDrive-Origin -ErrorAction SilentlyContinue)
if($existing.Count -gt 1 -or ($existing.Count -and $existing[0].Description -ne 'Managed by SmartDrive origin TLS setup')){
    throw 'An unrelated or ambiguous SmartDrive-Origin firewall rule exists. Review it before proceeding.'
}
if($PSCmdlet.ShouldProcess($origin.listenAddress,'Protect TLS certificate/key for LocalService and restrict the HTTPS origin to explicit connector/client addresses')){
    Set-OriginCertificateAccess $origin.privateKeyPath
    Set-OriginCertificateAccess $origin.certificatePath
    $policy=Get-OriginFirewallPolicy $origin $NodePath
    if($existing.Count){Set-NetFirewallRule @policy|Out-Null}
    else{New-NetFirewallRule @policy -DisplayName 'SmartDrive HTTPS origin'|Out-Null}
    Write-Output 'Origin TLS files protected and connector-scoped firewall policy applied. SmartDrive remains stopped for verified-TLS acceptance.'
}
