param(
    [Parameter(Mandatory=$true)][ValidateRange(1,999999999)][int]$LocationId,
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z_][a-zA-Z0-9_.-]{0,63}$')][string]$User,
    [Parameter(Mandatory=$true)][string]$IdentityFile,
    [string]$Cloudflared=(Join-Path $env:ProgramFiles 'SmartStock\app\dependency\cloudflared\windows-amd64\cloudflared.exe'),
    [switch]$PrintCommand
)
$ErrorActionPreference='Stop'
$Cloudflared=(Resolve-Path -LiteralPath $Cloudflared).Path
$IdentityFile=(Resolve-Path -LiteralPath $IdentityFile).Path
if($Cloudflared -match '["\r\n]' -or $IdentityFile -match '["\r\n]'){throw 'Invalid executable or identity path.'}
if((Get-FileHash -LiteralPath $Cloudflared -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'c29eee2b121f5436a642eed69fd9767da7e7b8c510fa50aaa130337f931357b5'){throw 'The tunnel client does not match the verified packaged build.'}
$hostName='store-'+$LocationId+'-admin.deckers.gy'
$directory=Invoke-RestMethod -Uri ('https://networking.deckers.gy/v1/stores/'+$LocationId+'/ssh-host-keys')
if($directory.locationId -ne $LocationId -or $directory.host -cne $hostName){throw 'The control plane returned a different store identity.'}
$keys=@($directory.hostKeys)
if(-not $keys.Count -or $keys.Count -gt 6 -or @($keys | Where-Object {$_ -notmatch '^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(?:256|384|521)) [A-Za-z0-9+/=]{40,8192}$'}).Count){throw 'No valid registered SSH host keys were returned.'}
$path=Join-Path $env:USERPROFILE '.ssh\smartstock-managed'
New-Item -ItemType Directory -Force -Path $path | Out-Null
$owner=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $path /inheritance:r /grant:r ('*'+$owner+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' | Out-Null
if($LASTEXITCODE -ne 0){throw 'Could not protect the managed SSH identity directory.'}
$knownHosts=Join-Path $path ('store-'+$LocationId+'-known_hosts')
Set-Content -LiteralPath $knownHosts -Encoding ASCII -Value @($keys | ForEach-Object {$hostName+' '+$_})
$proxy='ProxyCommand="'+$Cloudflared+'" access ssh --hostname %h'
$sshArgs=@('-o','StrictHostKeyChecking=yes','-o',('UserKnownHostsFile='+$knownHosts),'-o',$proxy,'-i',$IdentityFile,($User+'@'+$hostName))
if($PrintCommand){
    Write-Output 'Verified the active server host keys. Connection requires Cloudflare Access/MFA and the approved SSH key.'
    Write-Output ('ssh '+(@($sshArgs | ForEach-Object {'"'+$_.Replace('"','\"')+'"'}) -join ' '))
} else {
    & ssh @sshArgs
    if($LASTEXITCODE -ne 0){throw 'SSH connection ended unsuccessfully. Check Access/MFA, approved key and server availability.'}
}
