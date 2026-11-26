$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '..\installer\windows\Origin-TlsPolicy.ps1')
$path=Join-Path ([IO.Path]::GetTempPath()) ('smartdrive-origin-acl-'+[guid]::NewGuid()+'.key')
try{
    [IO.File]::WriteAllText($path,'synthetic fixture only; no real key')
    Set-OriginCertificateAccess $path
    $acl=Get-Acl -LiteralPath $path
    $rules=@($acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier]))
    if(-not $acl.AreAccessRulesProtected -or $rules.Count -ne 3){throw 'Origin key ACL inherited or included unrelated access.'}
    $service=@($rules|Where-Object {$_.IdentityReference.Value -eq 'S-1-5-19'})
    if($service.Count -ne 1 -or ($service[0].FileSystemRights -band [Security.AccessControl.FileSystemRights]::Write)){throw 'LocalService origin-key access was not read-only.'}
    foreach($rule in $rules){if($rule.IdentityReference.Value -notin @('S-1-5-18','S-1-5-32-544','S-1-5-19') -or $rule.IsInherited){throw 'Unexpected origin key principal.'}}
    $policy=Get-OriginFirewallPolicy ([pscustomobject]@{listenAddress='192.168.1.60';port=4178;allowedAddresses=@('192.168.1.10')}) 'C:\Program Files\nodejs\node.exe'
    if($policy.LocalAddress -ne '192.168.1.60' -or $policy.LocalPort -ne 4178 -or ($policy.RemoteAddress -join ',') -ne '192.168.1.10' -or 'Public' -in $policy.Profile){throw 'Origin firewall policy broadened its scope.'}
    Write-Output 'Origin ACL and firewall-policy fixtures passed. No firewall rules or services changed.'
}finally{if(Test-Path -LiteralPath $path){[IO.File]::Delete($path)}}
