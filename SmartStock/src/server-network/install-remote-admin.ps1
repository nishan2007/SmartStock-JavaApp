function Install-SmartStockRemoteAdministrator {
    param([Parameter(Mandatory)][string]$ServiceUser,[Parameter(Mandatory)][string]$ServiceHome)
    $ErrorActionPreference='Stop'
    $config=Join-Path $env:ProgramData 'ssh\sshd_config'
    if ((Test-Path -LiteralPath $config) -or (Get-Service sshd -ErrorAction SilentlyContinue)) {
        Write-Output 'Preserved the existing OpenSSH configuration; verify its approved keys separately.'
        return
    }
    $account=([Security.Principal.NTAccount]::new($ServiceUser)).Translate([Security.Principal.SecurityIdentifier])
    $local=Get-LocalUser | Where-Object {$_.SID.Value -eq $account.Value}
    if (-not $local) {throw 'Managed SSH setup requires a local server account; configure domain-account SSH separately.'}
    $name=$local.Name.ToLowerInvariant()
    if($name -notmatch '^[a-z0-9._-]+$' -or $ServiceHome -match '[\r\n"]') {throw 'Unsupported server account or profile path.'}
    $cap=Get-WindowsCapability -Online -Name 'OpenSSH.Server~~~~0.0.1.0'
    if($cap.State -ne 'Installed') {
        $install=Add-WindowsCapability -Online -Name 'OpenSSH.Server~~~~0.0.1.0'
        if($install.RestartNeeded) {throw 'Restart Windows and resume server setup to finish OpenSSH installation.'}
    }
    $sshDir=Join-Path $env:ProgramData 'ssh'
    New-Item -ItemType Directory -Path $sshDir -Force | Out-Null
    $keys=(Join-Path $ServiceHome '.ssh\authorized_keys').Replace('\','/')
    # No LAN/public SSH listener, password authentication or shared admin key file.
    @"
Port 22
ListenAddress 127.0.0.1
PubkeyAuthentication yes
PasswordAuthentication no
AuthenticationMethods publickey
AllowUsers $name
AuthorizedKeysFile "$keys"
Subsystem sftp sftp-server.exe
"@ | Set-Content -LiteralPath $config -Encoding ASCII
    & icacls.exe $config /inheritance:r /grant:r '*S-1-5-18:F' '*S-1-5-32-544:F' | Out-Null
    if($LASTEXITCODE -ne 0) {throw 'Could not protect the OpenSSH configuration.'}
    $sshd=Join-Path $env:SystemRoot 'System32\OpenSSH\sshd.exe'
    & (Join-Path $env:SystemRoot 'System32\OpenSSH\ssh-keygen.exe') -A
    if($LASTEXITCODE -ne 0) {throw 'Could not generate the SSH server host identity.'}
    Get-ChildItem -LiteralPath $sshDir -Filter 'ssh_host_*_key.pub' | ForEach-Object {
        & icacls.exe $_.FullName /grant '*S-1-5-32-545:R' | Out-Null
        if($LASTEXITCODE -ne 0) {throw 'Could not make the SSH public host identity readable.'}
    }
    & $sshd -t -f $config
    if($LASTEXITCODE -ne 0) {throw 'OpenSSH rejected the loopback configuration.'}
    Set-Service sshd -StartupType Automatic
    Start-Service sshd
    $marker=Join-Path $ServiceHome '.smartstock\network-provisioning\managed-ssh-keys.enabled'
    New-Item -ItemType Directory -Force -Path (Split-Path $marker) | Out-Null
    Set-Content -LiteralPath $marker -Value $account.Value -Encoding ASCII
    Write-Output 'Installed key-only SSH on loopback for the approved server account.'
}
