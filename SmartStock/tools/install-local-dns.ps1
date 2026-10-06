param(
    [string]$ServerAddress = '10.1.1.221',
    [string]$RouterAddress = '10.1.1.1',
    [string]$ClientSubnet = '10.1.1.0/24',
    [string]$WebHost = 'studio.deckers.gy',
    [string]$VerifiedArchive,
    [string]$InstallDirectory = (Join-Path $env:ProgramData 'SmartStockLocalDns')
)
$ErrorActionPreference = 'Stop'
$principal = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run this script as administrator to create the startup task and LAN-scoped firewall rules.'
}
foreach ($value in @($ServerAddress,$RouterAddress)) {
    $address = [Net.IPAddress]::Parse($value)
    $bytes = $address.GetAddressBytes()
    if ($bytes.Length -ne 4 -or -not ($bytes[0] -eq 10 -or ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) -or ($bytes[0] -eq 192 -and $bytes[1] -eq 168))) {
        throw 'DNS addresses must be private IPv4 addresses.'
    }
}
if ($ServerAddress -eq $RouterAddress) { throw 'Server and router addresses must differ.' }
if ($ClientSubnet -notmatch '^\d+\.\d+\.\d+\.\d+/\d{1,2}$') { throw 'Provide the store client subnet in CIDR notation.' }
if ($WebHost -notmatch '^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+deckers\.gy$') { throw 'Use a Deckers subdomain.' }
$taskName = 'SmartStockLocalDns'
$InstallDirectory = [IO.Path]::GetFullPath($InstallDirectory)
$exe = Join-Path $InstallDirectory 'coredns.exe'
$config = Join-Path $InstallDirectory 'Corefile'
$existing = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($existing -and @($existing.Actions | Where-Object {$_.Execute -ne $exe}).Count) { throw 'The existing DNS task has an unexpected executable.' }
New-Item -ItemType Directory -Force -Path $InstallDirectory | Out-Null
# A SYSTEM startup task must never execute binaries or configuration writable by regular users.
& icacls $InstallDirectory /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-32-545:(OI)(CI)RX' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not protect the DNS installation directory.' }
$archive = Join-Path $InstallDirectory 'coredns_1.14.7_windows_amd64.zip'
if ($VerifiedArchive) { Copy-Item -LiteralPath $VerifiedArchive -Destination $archive -Force }
else { Invoke-WebRequest -UseBasicParsing 'https://github.com/coredns/coredns/releases/download/v1.14.7/coredns_1.14.7_windows_amd64.zip' -OutFile $archive }
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne '6b718a5ed57d7034b1e4c3df500121307830da291d15cf8387f41c297c6fdd56') {
    throw 'The official CoreDNS archive failed its checksum check.'
}
if ($existing) { Stop-ScheduledTask -TaskName $taskName; Start-Sleep -Seconds 1 }
Expand-Archive -LiteralPath $archive -DestinationPath $InstallDirectory -Force
if (Test-Path -LiteralPath $config) { Copy-Item -LiteralPath $config -Destination ($config + '.backup-' + (Get-Date -Format 'yyyyMMddHHmmss')) }
@"
.:53 {
    bind $ServerAddress
    hosts {
        $ServerAddress $WebHost
        ttl 60
        no_reverse
        fallthrough
    }
    forward . $RouterAddress
    cache 30
    errors
}
"@ | Set-Content -LiteralPath $config -Encoding ASCII
foreach ($protocol in @('UDP','TCP')) {
    $ruleName = 'SmartStockLocalDns-' + $protocol
    if (-not (Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -Name $ruleName -DisplayName ('SmartStock local DNS ' + $protocol) -Direction Inbound -Action Allow -Protocol $protocol -LocalPort 53 -LocalAddress $ServerAddress -RemoteAddress $ClientSubnet -Program $exe -Profile Any | Out-Null
    }
}
$action = New-ScheduledTaskAction -Execute $exe -Argument ('-conf "' + $config + '"') -WorkingDirectory $InstallDirectory
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$taskPrincipal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $taskPrincipal -Force | Out-Null
Start-ScheduledTask -TaskName $taskName
Start-Sleep -Seconds 3
$local = Resolve-DnsName -Name $WebHost -Server $ServerAddress -Type A -DnsOnly -NoHostsFile -ErrorAction Stop
if (-not ($local | Where-Object {$_.IPAddress -eq $ServerAddress})) { throw 'The installed DNS resolver did not return the store address.' }
Resolve-DnsName -Name 'www.deckers.gy' -Server $ServerAddress -Type A -DnsOnly -NoHostsFile -ErrorAction Stop | Out-Null
Resolve-DnsName -Name $WebHost -Server $ServerAddress -Type A -TcpOnly -DnsOnly -NoHostsFile -ErrorAction Stop | Out-Null
Write-Host 'Local DNS startup task installed and local/public DNS verification passed.'
Write-Host "Cudy DHCP Preferred DNS: $ServerAddress. Leave Alternate DNS empty so clients consistently resolve the private name."
Write-Host 'Keep the router upstream DNS unchanged. Do not forward port 53 from the internet.'
