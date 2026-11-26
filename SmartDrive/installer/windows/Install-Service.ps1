[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][string]$ConfigPath,
    [string]$InstallRoot = 'C:\Program Files\SmartDrive',
    [string]$StateRoot = 'C:\ProgramData\SmartDrive',
    [string]$NodePath = (Get-Command node.exe -ErrorAction Stop).Source
)
$ErrorActionPreference = 'Stop'
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run service installation as an administrator inside the SmartDrive guest.'
}
if (Get-Service SmartDrive -ErrorAction SilentlyContinue) {
    throw 'SmartDrive is already installed. Stop and review the installed service before replacing it.'
}
$ConfigPath = (Resolve-Path -LiteralPath $ConfigPath).Path
$NodePath = (Resolve-Path -LiteralPath $NodePath).Path
$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
foreach ($path in @($InstallRoot, $StateRoot, $NodePath, $ConfigPath)) {
    if ($path -notmatch '^[A-Za-z]:\\' -or $path -match '["\r\n]') { throw 'Use absolute local paths.' }
}
if (Test-Path -LiteralPath $InstallRoot) { throw 'Choose a new installation directory.' }
if ($config.office -and (Get-LocalUser -Name sdpreview -ErrorAction SilentlyContinue)) {
    throw 'The dedicated preview account already exists. Review it before reinstalling.'
}
$compiler = Join-Path $env:SystemRoot 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path -LiteralPath $compiler)) { throw 'The Windows .NET Framework C# compiler is required.' }
$source = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$brandJson = & $NodePath (Join-Path $source 'src\branding.js') $ConfigPath
if ($LASTEXITCODE) { throw 'Invalid installation branding or storage root.' }
$brand = ($brandJson -join [Environment]::NewLine) | ConvertFrom-Json
$originJson = & $NodePath (Join-Path $source 'tools\validate-origin.mjs') $ConfigPath
if ($LASTEXITCODE) { throw 'Validate the origin certificate, key and private connector addresses before installation.' }
$origin = ($originJson -join [Environment]::NewLine) | ConvertFrom-Json
if ($origin.tls -and (Get-NetFirewallRule -Name SmartDrive-Origin -ErrorAction SilentlyContinue)) { throw 'The origin firewall rule already exists. Review it before first installation.' }
$storeIds = @($config.storeRoots.PSObject.Properties.Name)
if (-not $storeIds.Count -or @($storeIds | Where-Object { $_ -notmatch '^[1-9][0-9]{0,8}$' }).Count) {
    throw 'Configure valid store IDs before service installation.'
}
if ($env:SMARTDRIVE_STUDIO_BRIDGE_TOKEN -and $env:SMARTDRIVE_STUDIO_BRIDGE_TOKEN -cnotmatch '^[a-f0-9]{64}$') {
    throw 'Studio bridge token must be 32 random bytes in lowercase hex.'
}
$scanners = @($config.scanners | Where-Object { $null -ne $_ })
if (@($scanners.id | Sort-Object -Unique).Count -ne $scanners.Count -or @($scanners.account | Sort-Object -Unique).Count -ne $scanners.Count) { throw 'Scanner IDs and accounts must be unique.' }
foreach ($scanner in $scanners) {
    if ($scanner.id -isnot [string] -or $scanner.account -isnot [string] -or $scanner.storeId -isnot [string] -or
        $scanner.id -notmatch '^[A-Za-z0-9][A-Za-z0-9_-]{0,47}$' -or $scanner.account -cnotmatch '^sdscan_[a-z0-9_]{1,12}$' -or $scanner.storeId -notin $storeIds) {
        throw 'Configure valid scanner accounts and assigned stores before service installation.'
    }
}
if (Get-Service SmartDriveAccounts -ErrorAction SilentlyContinue) { throw 'The workstation account service already exists. Review it before reinstalling.' }
if ($config.scanStateRoot -and ($config.defenderScanner -isnot [string] -or $config.defenderScanner -notmatch '^[A-Za-z]:\\' -or
    [IO.Path]::GetFileName($config.defenderScanner) -ine 'MpCmdRun.exe' -or -not (Test-Path -LiteralPath $config.defenderScanner -PathType Leaf))) {
    throw 'Configure the installed Windows Defender MpCmdRun.exe before enabling scanner management.'
}
if ($config.office) {
    $converter = [IO.Path]::GetFullPath($config.office.converter)
    if ([IO.Path]::GetFileName($converter) -ine 'soffice.bin' -or -not (Test-Path -LiteralPath $converter -PathType Leaf)) {
        throw 'Install LibreOffice in the guest and configure its soffice.bin path.'
    }
}
& $NodePath -e "if(typeof fetch!=='function'||typeof Headers.prototype.getSetCookie!=='function'||typeof AbortSignal.timeout!=='function'||typeof require('node:fs/promises').statfs!=='function')process.exit(1)"
if ($LASTEXITCODE) { throw 'The selected Node runtime lacks APIs required by SmartDrive.' }
& (Join-Path $PSScriptRoot 'Copy-AppPayload.ps1') -SourceRoot $source -Destination $InstallRoot -ValidateOnly
if ($PSCmdlet.ShouldProcess($InstallRoot, 'Install SmartDrive service with LocalService identity')) {
    [void][IO.Directory]::CreateDirectory($InstallRoot)
    & icacls.exe $InstallRoot /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)RX' | Out-Null
    if ($LASTEXITCODE) { throw 'Could not protect application binaries.' }
    [void][IO.Directory]::CreateDirectory($StateRoot)
    & icacls.exe $StateRoot /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)M' | Out-Null
    if ($LASTEXITCODE) { throw 'Could not secure service state.' }
    & (Join-Path $PSScriptRoot 'Copy-AppPayload.ps1') -SourceRoot $source -Destination $InstallRoot
    $serviceExe = Join-Path $InstallRoot 'SmartDriveService.exe'
    & $compiler /nologo /target:exe /reference:System.ServiceProcess.dll /reference:System.Web.Extensions.dll ("/out:" + $serviceExe) (Join-Path $PSScriptRoot 'SmartDriveService.cs')
    if ($LASTEXITCODE) { throw 'Service wrapper compilation failed.' }
    $accountExe = Join-Path $InstallRoot 'SmartDriveAccounts.exe'
    & $compiler /nologo /target:exe /reference:System.ServiceProcess.dll /reference:System.Web.Extensions.dll ("/out:" + $accountExe) (Join-Path $PSScriptRoot 'SmartDriveAccounts.cs')
    if ($LASTEXITCODE) { throw 'Account service compilation failed.' }
    if ($config.office) {
        & $compiler /nologo /target:exe ("/out:" + (Join-Path $InstallRoot 'OfficePreview.exe')) (Join-Path $PSScriptRoot 'OfficePreview.cs')
        if ($LASTEXITCODE) { throw 'Office preview runner compilation failed.' }
        New-NetFirewallRule -Name SmartDrive-Office-NoNetwork -DisplayName 'SmartDrive Office preview network isolation' -Direction Outbound -Action Block -Program $converter -Profile Any | Out-Null
    }
    @{ storeIds=$storeIds;dataRoot=$brand.dataRoot; scanners=@($scanners | ForEach-Object { @{account=$_.account;storeId=$_.storeId} }) } |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $InstallRoot 'account-stores.json') -Encoding UTF8
    $key = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($key) } finally { $random.Dispose() }
    $environment = @{ SMARTDRIVE_FILE_ID_KEY = ([BitConverter]::ToString($key)).Replace('-', '').ToLowerInvariant() }
    if ($config.office) {
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        $passwordBytes = New-Object byte[] 32
        try { $random.GetBytes($passwordBytes) } finally { $random.Dispose() }
        $previewPassword = 'Sd!9' + [Convert]::ToBase64String($passwordBytes)
        $securePreview = ConvertTo-SecureString $previewPassword -AsPlainText -Force
        try {
            New-LocalUser -Name sdpreview -Password $securePreview -UserMayNotChangePassword -PasswordNeverExpires -Description 'SmartDrive isolated Office previews' | Out-Null
            $usersGroup = (Get-LocalGroup -SID 'S-1-5-32-545').Name
            Add-LocalGroupMember -Group $usersGroup -Member sdpreview
        } finally { $securePreview.Dispose() }
        $environment.SMARTDRIVE_PREVIEW_USER = 'sdpreview'
        $environment.SMARTDRIVE_PREVIEW_PASSWORD = $previewPassword
        $previewPassword = $null
    }
    if ($env:SMARTDRIVE_ONEDRIVE_CLIENT_SECRET) {
        $environment.SMARTDRIVE_ONEDRIVE_CLIENT_SECRET = $env:SMARTDRIVE_ONEDRIVE_CLIENT_SECRET
    }
    if ($env:SMARTDRIVE_STUDIO_BRIDGE_TOKEN) {
        $environment.SMARTDRIVE_STUDIO_BRIDGE_TOKEN = $env:SMARTDRIVE_STUDIO_BRIDGE_TOKEN
    }
    $installedConfig = Join-Path $StateRoot 'installation.json'
    Copy-Item -LiteralPath $ConfigPath -Destination $installedConfig
    $settings = @{ appRoot=$InstallRoot; nodePath=$NodePath; configPath=$installedConfig;
        logPath=(Join-Path $StateRoot 'service.log'); environment=$environment }
    $settingsPath = Join-Path $InstallRoot 'service.json'
    $settings | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $settingsPath -Encoding UTF8
    & icacls.exe $settingsPath /inheritance:r /grant:r '*S-1-5-18:F' '*S-1-5-32-544:F' '*S-1-5-19:R' | Out-Null
    if ($LASTEXITCODE) { throw 'Could not protect service credentials.' }
    $arguments = @{ Name='SmartDrive'; BinaryPathName=('"'+$serviceExe+'"'); StartupType='Automatic';
        DisplayName='SmartDrive'; Description='Smart Suite file previews, management, scanner intake and recovery';
        Credential=(New-Object Management.Automation.PSCredential('NT AUTHORITY\LocalService',(New-Object Security.SecureString))) }
    New-Service -Name SmartDriveAccounts -BinaryPathName ('"'+$accountExe+'"') -DisplayName 'SmartDrive workstation accounts' -StartupType Automatic | Out-Null
    $arguments.DependsOn = @('SmartDriveAccounts')
    New-Service @arguments | Out-Null
    if ($origin.tls) {
        & (Join-Path $PSScriptRoot 'Configure-OriginTls.ps1') -ConfigPath $installedConfig -NodePath $NodePath
    }
    . (Join-Path $PSScriptRoot 'Service-Recovery.ps1')
    Set-SmartDriveServiceRecovery
    Write-Output 'Service installed and left stopped. Grant LocalService access to configured data paths and verify the configuration before starting.'
}
