param(
    [string]$WebHost = 'store-lan.deckers.gy',
    [Parameter(Mandatory=$true)][string]$ServerAddress,
    [switch]$Initialize,
    [switch]$AcceptAcmeTerms,
    [switch]$ReloadServerService
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if ($WebHost -notmatch '^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+deckers\.gy$') { throw 'Use a Deckers DNS hostname.' }
$address = [Net.IPAddress]::Parse($ServerAddress).GetAddressBytes()
if ($address.Length -ne 4 -or $address[0] -ne 10) { throw 'Expected the private store server address.' }
if (-not (Get-NetIPAddress -AddressFamily IPv4 | Where-Object IPAddress -eq $ServerAddress)) { throw 'Run on the store server that owns this address.' }
$directory = Join-Path $env:USERPROFILE '.smartstock'
$acme = Join-Path $directory 'acme'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $acme /inheritance:r /grant:r ('*' + $identity + ':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not protect the certificate directory.' }
$env:POSHACME_HOME = Join-Path $acme 'posh-acme'
New-Item -ItemType Directory -Force -Path $env:POSHACME_HOME | Out-Null
Import-Module Posh-ACME
Set-PAServer LE_PROD
$token = Import-Clixml -LiteralPath (Join-Path $acme 'cloudflare-token.xml')
$credentials = Join-Path $directory 'credentials'
New-Item -ItemType Directory -Force -Path $credentials | Out-Null
$passwordPath = Join-Path $credentials 'mobile-web-keystore-password.dpapi'
if (-not (Test-Path -LiteralPath $passwordPath)) {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -NoNewline -LiteralPath $passwordPath
    [Array]::Clear($bytes,0,$bytes.Length)
}
$pfxPassword = Get-Content -Raw -LiteralPath $passwordPath | ConvertTo-SecureString
try {
    if ($Initialize) {
        if (-not $AcceptAcmeTerms) { throw 'Initial issuance requires explicit -AcceptAcmeTerms for the certificate authority terms.' }
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($token)
        try {
            $headers = @{Authorization = 'Bearer ' + [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)}
            $zone = @( (Invoke-RestMethod 'https://api.cloudflare.com/client/v4/zones?name=deckers.gy' -Headers $headers).result )
            if ($zone.Count -ne 1) { throw 'Could not uniquely identify the Deckers DNS zone.' }
            $url = 'https://api.cloudflare.com/client/v4/zones/' + $zone[0].id + '/dns_records'
            $records = @((Invoke-RestMethod ($url + '?name=' + $WebHost) -Headers $headers).result)
            if ($records.Count -eq 0) {
                $body = @{type='A';name=$WebHost;content=$ServerAddress;ttl=60;proxied=$false} | ConvertTo-Json -Compress
                $result = Invoke-RestMethod $url -Method Post -Headers $headers -ContentType 'application/json' -Body $body
                if (-not $result.success) { throw 'Could not create the local hostname DNS record.' }
            } elseif ($records.Count -ne 1 -or $records[0].type -ne 'A' -or $records[0].content -ne $ServerAddress -or $records[0].proxied) {
                throw 'Existing DNS differs from the intended private address; no record was overwritten.'
            }
        } finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
            $headers = $null
        }
        New-PACertificate -Domain $WebHost -Plugin Cloudflare -PluginArgs @{CFToken=$token} -PfxPassSecure $pfxPassword -AcceptTOS -DnsSleep 60 | Out-Null
    } else {
        Set-PAOrder -MainDomain $WebHost
        Submit-Renewal -MainDomain $WebHost -PluginArgs @{CFToken=$token} | Out-Null
    }
    $cert = Get-PACertificate -MainDomain $WebHost
    if (-not $cert.PfxFullChain -or -not (Test-Path -LiteralPath $cert.PfxFullChain)) { throw 'No full-chain PFX was produced.' }
    $target = Join-Path $directory ('mobile-web-' + $WebHost + '.p12')
    $java = Join-Path $env:ProgramFiles 'SmartStock\runtime\bin\java.exe'
    if (-not (Test-Path -LiteralPath $java)) { throw 'SmartStock Java runtime was not found.' }
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($pfxPassword)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) | & $java -cp $acme ImportMobileWebCertificate $cert.PfxFullChain $target $WebHost
        if ($LASTEXITCODE -ne 0) { throw 'Browser certificate validation/import failed.' }
    } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    if ($Initialize) {
        $settings = Join-Path $directory 'mobile-web.properties'
        if (Test-Path -LiteralPath $settings) { Copy-Item -LiteralPath $settings -Destination ($settings + '.before-public-certificate-' + (Get-Date -Format yyyyMMddHHmmss)) }
        Set-Content -LiteralPath $settings -Encoding ASCII -Value ('host=' + $WebHost)
    }
    Write-Output ('Browser certificate ready: https://' + $WebHost + ':8444/mobile-management/')
    Write-Output ('Expires: ' + $cert.NotAfter)
    if ($ReloadServerService) {
        $client = [Net.Sockets.TcpClient]::new()
        $stream = $null
        try {
            $client.Connect($ServerAddress,8444)
            $stream = [Net.Security.SslStream]::new($client.GetStream(),$false)
            $stream.AuthenticateAsClient($WebHost)
            $served = [Security.Cryptography.X509Certificates.X509Certificate2]::new($stream.RemoteCertificate)
            $reload = $served.Thumbprint -ne $cert.Thumbprint
        } catch { $reload = $true } finally {
            if ($stream) { $stream.Dispose() }
            $client.Dispose()
        }
        if ($reload) {
            $task = Get-ScheduledTask -TaskName 'SmartStockServerService'
            if ($task.Principal.UserId -notin @($env:USERNAME,($env:COMPUTERNAME + '\' + $env:USERNAME)) -or
                $task.Actions.Count -ne 1 -or $task.Actions[0].Execute -ne $java -or
                $task.Actions[0].Arguments -notmatch '--sync-service') {
                throw 'The server task does not match the expected SmartStock runtime and user.'
            }
            Stop-ScheduledTask -TaskName 'SmartStockServerService'
            Start-Sleep -Seconds 2
            Start-ScheduledTask -TaskName 'SmartStockServerService'
            Write-Output 'Restarted the existing SmartStock server task to load the certificate.'
        }
    } else {
        Write-Output 'The running mobile listener must reload before it serves the new certificate.'
    }
} finally {
    $token.Dispose()
    $pfxPassword.Dispose()
}
