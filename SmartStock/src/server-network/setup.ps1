param()
$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
$root = Join-Path $env:USERPROFILE '.smartstock\network-provisioning'
$config = Get-Content -Raw -LiteralPath (Join-Path $root 'bootstrap.json') | ConvertFrom-Json
. (Join-Path $root 'SmartStock.ps1')
$address = Get-NetIPAddress -AddressFamily IPv4 | Where-Object IPAddress -eq $config.lanAddress
if (-not $address) { throw 'The selected LAN address no longer belongs to this server.' }
if ($address.PrefixOrigin -ne 'Manual') { throw 'Reserve and configure a static store server LAN address before automatic publication.' }
$env:POSHACME_HOME = Join-Path $root 'acme'
$env:POSHACME_PLUGINS = $root
New-Item -ItemType Directory -Force -Path $env:POSHACME_HOME | Out-Null
if (-not (Get-Module -ListAvailable Posh-ACME | Where-Object Version -eq '4.35.0')) {
    [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
    if (-not (Get-PackageProvider -Name NuGet -ListAvailable -ErrorAction SilentlyContinue)) {
        Install-PackageProvider -Name NuGet -MinimumVersion 2.8.5.201 -Scope CurrentUser -Force | Out-Null
    }
    Install-Module Posh-ACME -RequiredVersion 4.35.0 -Repository PSGallery -Scope CurrentUser -Force
}
Import-Module Posh-ACME -RequiredVersion 4.35.0
Set-PAServer LE_PROD
$credentials = Join-Path $env:USERPROFILE '.smartstock\credentials'
New-Item -ItemType Directory -Force -Path $credentials | Out-Null
$passwordPath = Join-Path $credentials 'mobile-web-keystore-password.dpapi'
if (-not (Test-Path -LiteralPath $passwordPath)) {
    $bytes=New-Object byte[] 32
    $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try {$rng.GetBytes($bytes)} finally {$rng.Dispose()}
    ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -NoNewline -LiteralPath $passwordPath
    [Array]::Clear($bytes,0,$bytes.Length)
}
$password = Get-Content -Raw -LiteralPath $passwordPath | ConvertTo-SecureString
try {
    $order=Get-PAOrder -MainDomain $config.lanHost -ErrorAction SilentlyContinue
    if (-not $order) {
        # Central provisioning is enabled only after the operator has reviewed CA terms.
        New-PACertificate -Domain $config.lanHost -Plugin SmartStock -PfxPassSecure $password -AcceptTOS -DnsSleep 60 | Out-Null
    } else {
        Set-PAOrder -MainDomain $config.lanHost
        Submit-Renewal -MainDomain $config.lanHost | Out-Null
    }
    $cert=Get-PACertificate -MainDomain $config.lanHost
    if (-not $cert.PfxFullChain) {throw 'No trusted full-chain certificate was produced.'}
    $target=Join-Path $env:USERPROFILE ('.smartstock\mobile-web-'+$config.lanHost+'.p12')
    $classpath=$config.jar+';'+(Join-Path (Split-Path $config.jar) 'dependency\*')
    if ($config.helperJar) {$classpath=$config.helperJar+';'+$classpath}
    $newHash=(Get-FileHash -LiteralPath $cert.PfxFullChain -Algorithm SHA256).Hash
    $stamp=Join-Path $root 'imported-pfx.sha256'
    $targetStamp=Join-Path $root 'imported-keystore.sha256'
    $intact=(Test-Path -LiteralPath $target) -and (Test-Path -LiteralPath $targetStamp) -and ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -eq (Get-Content -Raw -LiteralPath $targetStamp).Trim())
    if (-not $intact -or -not (Test-Path -LiteralPath $stamp) -or (Get-Content -Raw -LiteralPath $stamp).Trim() -ne $newHash) {
        $ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
        try {
            [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) | & $config.java -cp $classpath services.MobileWebCertificateImporter $cert.PfxFullChain $target $config.lanHost
            if ($LASTEXITCODE -ne 0) {throw 'Browser certificate validation failed.'}
        } finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)}
        Set-Content -LiteralPath $stamp -Value $newHash -Encoding ASCII
        Set-Content -LiteralPath $targetStamp -Value (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -Encoding ASCII
    }
    $settings=Join-Path $env:USERPROFILE '.smartstock\mobile-web.properties'
    # Keep a rollback copy once; never replace the register keystore or cloud credentials.
    if ((Test-Path -LiteralPath $settings) -and -not (Test-Path -LiteralPath ($settings+'.before-provisioning'))) {
        Copy-Item -LiteralPath $settings -Destination ($settings+'.before-provisioning')
    }
    $lines=if (Test-Path -LiteralPath $settings) {@(Get-Content -LiteralPath $settings | Where-Object {$_ -notmatch '^\s*host\s*='})} else {@()}
    $lines+=('host='+$config.lanHost)
    Set-Content -LiteralPath ($settings+'.new') -Value $lines -Encoding ASCII
    Move-Item -LiteralPath ($settings+'.new') -Destination $settings -Force
    # Trust only the public register certificate for this connector's loopback hop.
    $public=[IO.File]::ReadAllBytes((Join-Path $env:USERPROFILE '.smartstock\lan-api.cer'))
    $caPath=Join-Path $root 'register-upstream.pem'
    $pem="-----BEGIN CERTIFICATE-----`n"+[Convert]::ToBase64String($public,[Base64FormattingOptions]::InsertLineBreaks)+"`n-----END CERTIFICATE-----`n"
    Set-Content -LiteralPath $caPath -Value $pem -Encoding ASCII
    $upstream=[Security.Cryptography.X509Certificates.X509Certificate2]::new($public)
    $tlsName=$upstream.GetNameInfo([Security.Cryptography.X509Certificates.X509NameType]::DnsName,$false)
    $hostKeys=@(Get-ChildItem -LiteralPath (Join-Path $env:ProgramData 'ssh') -Filter 'ssh_host_*_key.pub' -ErrorAction SilentlyContinue | ForEach-Object {
        $parts=(Get-Content -Raw -LiteralPath $_.FullName).Trim().Split(' ',[StringSplitOptions]::RemoveEmptyEntries)
        if($parts.Count -ge 2){$parts[0]+' '+$parts[1]}
    })
    $manifest=Invoke-SmartStockNetwork '/v1/provision' @{lanAddress=$config.lanAddress;caPath=$caPath;upstreamTlsName=$tlsName;sshHostKeys=$hostKeys}
    if ($manifest.remoteReady) {
        $sshMarker=Join-Path $root 'managed-ssh-keys.enabled'
        if(Test-Path -LiteralPath $sshMarker){
            $owner=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
            if((Get-Content -Raw -LiteralPath $sshMarker).Trim() -ne $owner){throw 'Managed SSH marker belongs to another server account.'}
            $sshDirectory=Join-Path $env:USERPROFILE '.ssh'
            New-Item -ItemType Directory -Force -Path $sshDirectory | Out-Null
            & icacls.exe $sshDirectory /inheritance:r /grant:r ('*'+$owner+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' | Out-Null
            if($LASTEXITCODE -ne 0){throw 'Could not protect the SSH key directory.'}
            $keys=@($manifest.sshPublicKeys)
            if($keys.Count -gt 4 -or @($keys | Where-Object {$_ -notmatch '^(ssh-ed25519|ssh-rsa) [A-Za-z0-9+/=]{40,8192}$'}).Count){throw 'Invalid approved SSH public keys.'}
            $authorized=Join-Path $sshDirectory 'authorized_keys'
            $existingKeys=if(Test-Path -LiteralPath $authorized){Get-Content -Raw -LiteralPath $authorized}else{''}
            $existingKeys=[regex]::Replace($existingKeys,'(?ms)^# BEGIN SMARTSTOCK MANAGED NETWORK\r?\n.*?^# END SMARTSTOCK MANAGED NETWORK\r?\n?','').TrimEnd()
            $block=if($keys.Count){"`n# BEGIN SMARTSTOCK MANAGED NETWORK`n"+($keys -join "`n")+"`n# END SMARTSTOCK MANAGED NETWORK`n"}else{"`n"}
            Set-Content -LiteralPath $authorized -Value ($existingKeys+$block) -Encoding ASCII
            & icacls.exe $authorized /inheritance:r /grant:r ('*'+$owner+':F') '*S-1-5-18:F' | Out-Null
            if($LASTEXITCODE -ne 0){throw 'Could not protect the SSH authorization file.'}
        }
        $cloudflared=Join-Path (Split-Path $config.jar) 'dependency\cloudflared\windows-amd64\cloudflared.exe'
        if ((Get-FileHash -LiteralPath $cloudflared -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'c29eee2b121f5436a642eed69fd9767da7e7b8c510fa50aaa130337f931357b5') {throw 'Packaged tunnel client integrity check failed.'}
        $tokenPath=Join-Path $root 'connector-token'
        # cloudflared --token-file needs a plaintext file; its parent ACL permits
        # only the server account and SYSTEM. Never pass tokens as arguments/logs.
        Set-Content -LiteralPath $tokenPath -Value $manifest.tunnelToken -NoNewline -Encoding ASCII
        $taskName='SmartStockManagedNetworkTunnel'
        $existing=Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
        $action=New-ScheduledTaskAction -Execute $cloudflared -Argument ('tunnel --no-autoupdate run --token-file "'+$tokenPath+'"')
        $principal=New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
        $trigger=New-ScheduledTaskTrigger -AtLogOn -User $principal.UserId
        $options=New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1)
        $oldId=Join-Path $root 'connector-id'
        $changed= -not (Test-Path -LiteralPath $oldId) -or (Get-Content -Raw -LiteralPath $oldId).Trim() -ne $manifest.tunnelId
        if ($existing -and ($existing.Actions.Count -ne 1 -or $existing.Actions[0].Execute -ne $cloudflared)) {throw 'An unrelated task uses the managed network task name.'}
        if ($existing -and $changed) {Stop-ScheduledTask -TaskName $taskName}
        Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -Trigger $trigger -Settings $options -Force | Out-Null
        Set-Content -LiteralPath $oldId -Value $manifest.tunnelId -Encoding ASCII
        if (-not $existing -or $changed -or $existing.State -ne 'Running') {Start-ScheduledTask -TaskName $taskName}
        $employeeSettings=Join-Path $env:USERPROFILE '.smartstock\employee-web.properties'
        if ((Test-Path -LiteralPath $employeeSettings) -and -not (Test-Path -LiteralPath ($employeeSettings+'.before-provisioning'))) {Copy-Item -LiteralPath $employeeSettings -Destination ($employeeSettings+'.before-provisioning')}
        $oldOrigin=if(Test-Path -LiteralPath $employeeSettings){@(Get-Content -LiteralPath $employeeSettings | Where-Object {$_ -match '^public\.origin='})}else{@()}
        if(-not $oldOrigin.Count -or $oldOrigin[0] -match '^public\.origin=https://store-\d+-web\.deckers\.gy$') {
            Set-Content -LiteralPath $employeeSettings -Value ('public.origin=https://'+$manifest.websiteHost) -Encoding ASCII
        } else {Write-Output 'Preserved the existing employee website origin during managed routing setup.'}
        # No secret material is written into the status summary.
        $manifest | Select-Object lanHost,websiteHost,adminHost,generation,remoteReady,tunnelId | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root 'endpoints.json') -Encoding UTF8
        $sshd=Get-Service -Name sshd -ErrorAction SilentlyContinue
        if (-not $sshd -or $sshd.Status -ne 'Running') {Write-Warning 'The protected administrator route is configured, but OpenSSH Server must be installed and running with approved administrator keys before SSH access works.'}
    }
    Write-Output ('Certificate and assigned hostname ready: '+$config.lanHost)
} finally {$password.Dispose()}
