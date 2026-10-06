param(
    [string]$ServerAddress,
    [string]$RouterAddress,
    [string]$ClientSubnet,
    [string]$WebHost = 'studio.deckers.gy',
    [string]$ServerUserHome,
    [string]$VerifiedArchive,
    [switch]$PlanOnly
)
$ErrorActionPreference = 'Stop'
function Convert-PrivateIPv4([string]$Value) {
    $ip = [Net.IPAddress]::Parse($Value)
    $b = $ip.GetAddressBytes()
    if ($b.Length -ne 4 -or -not ($b[0] -eq 10 -or ($b[0] -eq 172 -and $b[1] -ge 16 -and $b[1] -le 31) -or ($b[0] -eq 192 -and $b[1] -eq 168))) {
        throw 'Select a private IPv4 address on the store network.'
    }
    return $b
}
function Get-NetworkBytes([byte[]]$Bytes,[int]$Prefix) {
    $network = [byte[]]::new(4)
    for ($i=0;$i -lt 4;$i++) {
        $bits = [Math]::Min(8,[Math]::Max(0,$Prefix-8*$i))
        $mask = if ($bits -eq 0) {0} else {256-[Math]::Pow(2,8-$bits)}
        $network[$i] = $Bytes[$i] -band [int]$mask
    }
    return ($network -join '.')
}
if (-not $ServerAddress -or -not $RouterAddress -or -not $ClientSubnet) {
    $choices = @(Get-NetIPConfiguration | Where-Object {$_.IPv4DefaultGateway -and $_.IPv4Address -and $_.IPv4Address.IPAddress -notlike '169.254.*'})
    if (-not $choices.Count) { throw 'No connected store network was found. Supply ServerAddress, RouterAddress and ClientSubnet explicitly.' }
    Write-Host 'Choose the network used by store computers and phones:'
    for ($i=0;$i -lt $choices.Count;$i++) {
        Write-Host ("{0}. {1} — server {2}, router {3}" -f ($i+1),$choices[$i].InterfaceAlias,$choices[$i].IPv4Address.IPAddress,$choices[$i].IPv4DefaultGateway.NextHop)
    }
    $selection = if ($choices.Count -eq 1) {1} else {[int](Read-Host 'Network number')}
    if ($selection -lt 1 -or $selection -gt $choices.Count) { throw 'Select a network from the list.' }
    $selected = $choices[$selection-1]
    if (-not $ServerAddress) {$ServerAddress = [string]$selected.IPv4Address.IPAddress}
    if (-not $RouterAddress) {$RouterAddress = [string]$selected.IPv4DefaultGateway.NextHop}
    if (-not $ClientSubnet) {
        $prefix = [int]$selected.IPv4Address.PrefixLength
        $ClientSubnet = (Get-NetworkBytes (Convert-PrivateIPv4 $ServerAddress) $prefix) + '/' + $prefix
    }
}
$serverBytes = Convert-PrivateIPv4 $ServerAddress
$routerBytes = Convert-PrivateIPv4 $RouterAddress
if ($ServerAddress -eq $RouterAddress) {throw 'The store server and router must have different addresses.'}
if ($ClientSubnet -notmatch '^(\d+\.\d+\.\d+\.\d+)/(\d{1,2})$') {throw 'Use a client subnet such as 192.168.5.0/24.'}
$subnetAddress = $Matches[1]; $prefix = [int]$Matches[2]
if ($prefix -lt 8 -or $prefix -gt 30) {throw 'Use a store subnet prefix between /8 and /30.'}
$subnetBytes = Convert-PrivateIPv4 $subnetAddress
$network = Get-NetworkBytes $subnetBytes $prefix
if ($network -ne $subnetAddress -or (Get-NetworkBytes $serverBytes $prefix) -ne $network -or (Get-NetworkBytes $routerBytes $prefix) -ne $network) {
    throw 'The server and router must belong to the specified store subnet, whose address must be its network address.'
}
$WebHost = $WebHost.Trim().ToLowerInvariant()
if ($WebHost.Length -gt 253 -or $WebHost -notmatch '^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+deckers\.gy$') {throw 'Use a Deckers subdomain such as studio.deckers.gy.'}
if (-not $ServerUserHome) {
    $task = Get-ScheduledTask -TaskName SmartStockServerService -ErrorAction SilentlyContinue
    if ($task) {
        $sid = ([Security.Principal.NTAccount]::new($task.Principal.UserId)).Translate([Security.Principal.SecurityIdentifier]).Value
        $ServerUserHome = [Environment]::ExpandEnvironmentVariables((Get-ItemProperty -LiteralPath ("Registry::HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion\ProfileList\"+$sid) -Name ProfileImagePath).ProfileImagePath)
    } else {$ServerUserHome = $env:USERPROFILE}
}
$ServerUserHome = [IO.Path]::GetFullPath($ServerUserHome)
$plan = [ordered]@{ServerAddress=$ServerAddress;RouterAddress=$RouterAddress;ClientSubnet=$ClientSubnet;WebHost=$WebHost;ServerUserHome=$ServerUserHome;StudioUrl="https://${WebHost}:8444/background-remover.html";RouterPreferredDNS=$ServerAddress;RouterAlternateDNS='Leave empty'}
if ($PlanOnly) {$plan | ConvertTo-Json; return}
$principal = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {throw 'Right-click Start Store Studio.cmd and choose Run as administrator.'}
if (-not (@(Get-NetIPConfiguration | ForEach-Object {$_.IPv4Address.IPAddress}) -contains $ServerAddress)) {throw 'Run this setup on the store server that owns the selected address.'}
$appDirectories=@((Join-Path $ServerUserHome '.smartstock\sync-service\app'),(Join-Path $env:ProgramFiles 'SmartStock\app'))
$supported=$false
foreach ($appDirectory in $appDirectories) {
    foreach ($jar in @(Get-ChildItem -LiteralPath $appDirectory -Filter 'inventory-management-*.jar' -ErrorAction SilentlyContinue)) {
        if ($jar.Name -match '^inventory-management-(\d+\.\d+\.\d+)\.jar$' -and [version]$Matches[1] -ge [version]'1.0.233') {$supported=$true}
    }
}
if (-not $supported) {throw 'Update this store server to SmartStock 1.0.233 or newer before running setup.'}
if (-not $VerifiedArchive -and (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'coredns_1.14.7_windows_amd64.zip'))) {$VerifiedArchive=Join-Path $PSScriptRoot 'coredns_1.14.7_windows_amd64.zip'}
Write-Host ('Store server: '+$ServerAddress+'  Router: '+$RouterAddress+'  Network: '+$ClientSubnet)
Write-Host 'Reserve this server address in the router before changing DHCP DNS.'
& (Join-Path $PSScriptRoot 'install-local-dns.ps1') -ServerAddress $ServerAddress -RouterAddress $RouterAddress -ClientSubnet $ClientSubnet -WebHost $WebHost -VerifiedArchive $VerifiedArchive
& (Join-Path $PSScriptRoot 'configure-local-web-address.ps1') -WebHost $WebHost -ServerUserHome $ServerUserHome
$nrpt = @(Get-DnsClientNrptRule | Where-Object {$_.Namespace -contains $WebHost})
if (-not $nrpt.Count) {Add-DnsClientNrptRule -Namespace $WebHost -NameServers $ServerAddress -Comment 'SmartStock private studio DNS' | Out-Null}
elseif (@($nrpt | Where-Object {$_.NameServers -ne $ServerAddress}).Count) {throw 'An existing studio DNS rule differs. Review it before changing it.'}
Clear-DnsClientCache
$plan | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $env:ProgramData 'SmartStockLocalDns\store-setup.json') -Encoding UTF8
Write-Host ''
Write-Host 'Server setup complete. Finish these three steps:'
Write-Host "1. In the router DHCP settings, set Preferred DNS to $ServerAddress and leave Alternate DNS empty. Keep upstream DNS and port forwards unchanged."
Write-Host '2. Use SmartStock 1.0.233 or newer. Stop/start the Mobile Item Web App in SmartStock to generate its browser certificate.'
Write-Host ("3. Copy only the public .cer certificate from " + (Join-Path $ServerUserHome ('.smartstock\mobile-web-'+$WebHost+'.cer')) + ' to staff computers, then run trust-store-studio.ps1 with its verified SHA-256 fingerprint.')
Write-Host ('Reconnect store devices, activate each browser in SmartStock, then open '+$plan.StudioUrl)
