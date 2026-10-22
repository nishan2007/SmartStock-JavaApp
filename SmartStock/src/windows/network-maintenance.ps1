param([switch]$Install, [string]$LanSubnet = '')
$ErrorActionPreference = 'Stop'
$root = Join-Path $env:ProgramData 'SmartStockNetwork'
$script = Join-Path $root 'network-maintenance.ps1'
$state = Join-Path $root 'settings.json'
$principal = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Administrator access is required.' }
function Protect-TaskDirectory([string]$directory) {
    $items = @(Get-Item -LiteralPath $directory) + @(Get-ChildItem -LiteralPath $directory -Recurse -Force)
    if ($items | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) { throw 'Task directories must not contain links.' }
    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetAccessRuleProtection($true, $false)
    $acl.SetOwner([Security.Principal.SecurityIdentifier]::new('S-1-5-32-544'))
    foreach ($spec in @(@('S-1-5-18','FullControl'),@('S-1-5-32-544','FullControl'),@('S-1-5-32-545','ReadAndExecute'))) {
        $rule = [Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($spec[0]), [Security.AccessControl.FileSystemRights]$spec[1], [Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit', [Security.AccessControl.PropagationFlags]::None, [Security.AccessControl.AccessControlType]::Allow)
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $directory -AclObject $acl
    foreach ($child in @(Get-ChildItem -LiteralPath $directory -Force)) {
        & icacls.exe $child.FullName /reset /T /Q | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not protect task directory contents.' }
    }
}
if (Test-Path -LiteralPath $root) {
    if ((Get-Item -LiteralPath $root).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Maintenance directory must not be a link.' }
}
if ($Install) {
    if (-not $LanSubnet) {
        if (Test-Path -LiteralPath $state) { $LanSubnet = (Get-Content -Raw -LiteralPath $state | ConvertFrom-Json).LanSubnet }
        else {
            $legacy = @(Get-NetFirewallRule -DisplayName 'SmartStock LAN API 8443' -ErrorAction SilentlyContinue | Get-NetFirewallAddressFilter)
            if ($legacy.Count -eq 1) { $LanSubnet = $legacy[0].RemoteAddress -join ',' }
        }
    }
    if (-not $LanSubnet) { $LanSubnet = 'LocalSubnet' }
    if ($LanSubnet -ne 'LocalSubnet') {
        foreach ($scope in ($LanSubnet -split ',')) {
            if ($scope -notmatch '^(\d{1,3}(?:\.\d{1,3}){3})(?:/(\d{1,2}))?$') { throw 'Use LocalSubnet or private IPv4 CIDR/IP scopes.' }
            $ip = [Net.IPAddress]::Parse($Matches[1]); $bytes = $ip.GetAddressBytes()
            if ($bytes.Length -ne 4 -or -not ($bytes[0] -eq 10 -or ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) -or ($bytes[0] -eq 192 -and $bytes[1] -eq 168))) { throw 'Only private LAN scopes are supported.' }
            if ($Matches[2] -and [int]$Matches[2] -gt 32) { throw 'Invalid IPv4 prefix.' }
        }
    }
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    Protect-TaskDirectory $root
    if ([IO.Path]::GetFullPath($PSCommandPath) -ne $script) { Copy-Item -LiteralPath $PSCommandPath -Destination $script -Force }
    $dnsHash = ''
    $dnsRoot = Join-Path $env:ProgramData 'SmartStockLocalDns'
    $dnsArchive = Join-Path $dnsRoot 'coredns_1.14.7_windows_amd64.zip'
    $dnsBinary = Join-Path $dnsRoot 'coredns.exe'
    if (Test-Path -LiteralPath $dnsBinary) {
        if (-not (Test-Path -LiteralPath $dnsArchive) -or (Get-FileHash -LiteralPath $dnsArchive -Algorithm SHA256).Hash.ToLowerInvariant() -ne '6b718a5ed57d7034b1e4c3df500121307830da291d15cf8387f41c297c6fdd56') { throw 'Local DNS requires its verified official archive before SYSTEM startup can be installed.' }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [IO.Compression.ZipFile]::OpenRead($dnsArchive)
        try {
            $entry = $zip.GetEntry('coredns.exe')
            if (-not $entry) { throw 'CoreDNS archive has no executable.' }
            $stream = $entry.Open(); $sha = [Security.Cryptography.SHA256]::Create()
            try { $dnsHash = ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','') }
            finally { $stream.Dispose(); $sha.Dispose() }
        } finally { $zip.Dispose() }
        if ((Get-FileHash -LiteralPath $dnsBinary -Algorithm SHA256).Hash -ne $dnsHash) { throw 'Installed DNS executable does not match the verified archive.' }
    }
    @{ LanSubnet = $LanSubnet; DnsHash = $dnsHash } | ConvertTo-Json | Set-Content -LiteralPath $state -Encoding ASCII
    $action = New-ScheduledTaskAction -Execute (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe') -Argument ('-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $script + '"')
    $boot = New-ScheduledTaskTrigger -AtStartup
    $boot.Delay = 'PT30S'
    $repeat = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 1)
    $settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 1) -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
    $system = New-ScheduledTaskPrincipal -UserId SYSTEM -LogonType ServiceAccount -RunLevel Highest
    Register-ScheduledTask -TaskName SmartStockNetworkMaintenance -Action $action -Trigger @($boot,$repeat) -Settings $settings -Principal $system -Force | Out-Null
}
$configuration = Get-Content -Raw -LiteralPath $state | ConvertFrom-Json
$scope = $configuration.LanSubnet
$log = Join-Path $root 'maintenance.log'
function Write-RepairLog([string]$message) {
    if ((Test-Path -LiteralPath $log) -and (Get-Item -LiteralPath $log).Length -gt 1048576) { Move-Item -LiteralPath $log -Destination ($log + '.previous') -Force }
    Add-Content -LiteralPath $log -Value ((Get-Date -Format o) + ' ' + $message)
}
try {
    foreach ($spec in @(
        @{Name='API';Port=8443;Protocol='TCP';Number='6'},
        @{Name='Discovery';Port=18443;Protocol='UDP';Number='17'},
        @{Name='MobileUI';Port=8444;Protocol='TCP';Number='6'},
        @{Name='MobileAPI';Port=8445;Protocol='TCP';Number='6'},
        @{Name='EmployeeRegistration';Port=8448;Protocol='TCP';Number='6'}
    )) {
        $name = 'SmartStockManaged-' + $spec.Name
        $rule = Get-NetFirewallRule -Name $name -ErrorAction SilentlyContinue
        $valid = $false
        if ($rule) {
            $ports = $rule | Get-NetFirewallPortFilter
            $addresses = $rule | Get-NetFirewallAddressFilter
            $application = $rule | Get-NetFirewallApplicationFilter
            $valid = $rule.Enabled -eq 'True' -and $rule.Direction -eq 'Inbound' -and $rule.Action -eq 'Allow' -and $rule.Profile -eq 'Private' -and ($ports.Protocol -in @($spec.Protocol,$spec.Number)) -and ($ports.LocalPort -join ',') -eq [string]$spec.Port -and ($addresses.RemoteAddress -join ',') -eq $scope -and ($addresses.LocalAddress -join ',') -eq 'Any'
            $valid = $valid -and $application.Program -eq 'Any' -and ($ports.RemotePort -join ',') -eq 'Any'
        }
        if (-not $valid) {
            if ($rule) { Remove-NetFirewallRule -Name $name }
            New-NetFirewallRule -Name $name -DisplayName ('SmartStock managed ' + $spec.Name) -Direction Inbound -Enabled True -Action Allow -Profile Private -Protocol $spec.Protocol -LocalPort $spec.Port -RemoteAddress ($scope -split ',') | Out-Null
            Write-RepairLog "Repaired $name (Private LAN only)."
        }
    }
    # DNS is optional. Retain each store's existing binary, addresses and Corefile.
    $dnsRoot = Join-Path $env:ProgramData 'SmartStockLocalDns'
    $dnsExe = Join-Path $dnsRoot 'coredns.exe'
    $dnsConfig = Join-Path $dnsRoot 'Corefile'
    if ($configuration.DnsHash -and (Test-Path -LiteralPath $dnsExe) -and (Test-Path -LiteralPath $dnsConfig)) {
        if ((Get-FileHash -LiteralPath $dnsExe -Algorithm SHA256).Hash -ne $configuration.DnsHash) { throw 'DNS executable changed; administrator verification required.' }
        foreach ($item in @(Get-Item -LiteralPath $dnsRoot) + @(Get-ChildItem -LiteralPath $dnsRoot -Recurse -Force)) {
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'DNS installation must not contain links.' }
        }
        Protect-TaskDirectory $dnsRoot
        $dnsText = Get-Content -Raw -LiteralPath $dnsConfig
        $bindMatch = [regex]::Match($dnsText, '(?m)^\s*bind\s+(\d{1,3}(?:\.\d{1,3}){3})\s*$')
        if (-not $bindMatch.Success) { throw 'DNS requires an explicit IPv4 LAN bind address.' }
        $bindAddress = $bindMatch.Groups[1].Value
        foreach ($protocol in @('TCP','UDP')) {
            $name = 'SmartStockManaged-DNS-' + $protocol
            $rule = Get-NetFirewallRule -Name $name -ErrorAction SilentlyContinue
            $valid = $false
            if ($rule) {
                $ports = $rule | Get-NetFirewallPortFilter
                $addresses = $rule | Get-NetFirewallAddressFilter
                $application = $rule | Get-NetFirewallApplicationFilter
                $valid = $rule.Enabled -eq 'True' -and $rule.Action -eq 'Allow' -and $rule.Direction -eq 'Inbound' -and $rule.Profile -eq 'Private' -and ($ports.Protocol -in @($protocol,$(if ($protocol -eq 'TCP') { '6' } else { '17' }))) -and ($ports.LocalPort -join ',') -eq '53' -and ($addresses.RemoteAddress -join ',') -eq $scope -and ($addresses.LocalAddress -join ',') -eq $bindAddress -and $application.Program -eq $dnsExe
            }
            if (-not $valid) {
                if ($rule) { Remove-NetFirewallRule -Name $name }
                New-NetFirewallRule -Name $name -DisplayName ('SmartStock managed DNS ' + $protocol) -Enabled True -Direction Inbound -Action Allow -Profile Private -Protocol $protocol -LocalPort 53 -LocalAddress $bindAddress -RemoteAddress ($scope -split ',') -Program $dnsExe | Out-Null
                Write-RepairLog "Repaired $name on configured LAN address."
            }
        }
        $dnsTask = Get-ScheduledTask -TaskName SmartStockLocalDns -ErrorAction SilentlyContinue
        if ($dnsTask -and @($dnsTask.Actions | Where-Object { $_.Execute -ne $dnsExe }).Count) { throw 'Unexpected DNS task executable; administrator review required.' }
        if (-not $dnsTask -or $Install) {
            $action = New-ScheduledTaskAction -Execute $dnsExe -Argument ('-conf "' + $dnsConfig + '"') -WorkingDirectory $dnsRoot
            $trigger = New-ScheduledTaskTrigger -AtStartup; $trigger.Delay = 'PT30S'
            $settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -MultipleInstances IgnoreNew -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
            $system = New-ScheduledTaskPrincipal -UserId SYSTEM -LogonType ServiceAccount -RunLevel Highest
            Register-ScheduledTask -TaskName SmartStockLocalDns -Action $action -Trigger $trigger -Settings $settings -Principal $system -Force | Out-Null
            Write-RepairLog 'Restored local DNS automatic startup.'
        }
        # Hand off the manually started process only during an explicit installation.
        if ($Install) { Get-Process -Name coredns -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $dnsExe } | Stop-Process -Force -Confirm:$false }
        if (-not (Get-Process -Name coredns -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $dnsExe })) {
            Start-ScheduledTask -TaskName SmartStockLocalDns
            Write-RepairLog 'Started local DNS task.'
        }
    }
} catch { Write-RepairLog $_.Exception.Message; throw }
