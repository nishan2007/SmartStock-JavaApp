[CmdletBinding(SupportsShouldProcess)]
param(
    [ValidateSet('Update','Repair','Rollback')][string]$Action='Update',
    [string]$InstallRoot='C:\Program Files\SmartDrive',
    [string]$SourceRoot,
    [string]$PreviousRoot,
    [switch]$MaintenanceWindow
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Service-Payload.ps1')
. (Join-Path $PSScriptRoot 'Service-Recovery.ps1')
if(-not $MaintenanceWindow){throw 'Stop both SmartDrive services during a maintenance window before updating, repairing or rolling back.'}
if(-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){
    throw 'Run service maintenance as an administrator inside the SmartDrive guest.'
}
$InstallRoot=[IO.Path]::GetFullPath($InstallRoot).TrimEnd('\');Assert-PayloadPath $InstallRoot
foreach($name in @('SmartDrive','SmartDriveAccounts')){if((Get-Service -Name $name -ErrorAction Stop).Status -ne 'Stopped'){throw 'Both SmartDrive services must be stopped.'}}
foreach($name in @('SmartDrive','SmartDriveAccounts')){
    $registered=Get-CimInstance Win32_Service -Filter ("Name='"+$name+"'")
    if($registered.PathName.Trim() -ine ('"'+(Join-Path $InstallRoot ($name+'.exe'))+'"')){throw 'Registered service binary paths do not match the selected installation.'}
}
$settingsPath=Join-Path $InstallRoot 'service.json';Assert-PayloadPath $settingsPath
$settings=Get-Content -LiteralPath $settingsPath -Raw|ConvertFrom-Json
Assert-PayloadPath (Join-Path $InstallRoot 'account-stores.json')
if([IO.Path]::GetFullPath($settings.appRoot).TrimEnd('\') -ne $InstallRoot){throw 'The service settings do not match the selected installation.'}
foreach($path in @($settings.configPath,$settings.nodePath)){
    Assert-PayloadPath $path
    if([IO.Path]::GetFullPath($path).StartsWith($InstallRoot+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Keep service configuration and the Node runtime outside the replaceable application directory.'}
}
$config=Get-Content -LiteralPath $settings.configPath -Raw|ConvertFrom-Json
$compiler=Join-Path $env:SystemRoot 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if(-not (Test-Path -LiteralPath $compiler -PathType Leaf)){throw 'The Windows .NET Framework compiler is required.'}
$parent=[IO.Path]::GetDirectoryName($InstallRoot);$tag=[guid]::NewGuid().ToString('N')
$retained=$InstallRoot+'.previous-'+$tag
if($Action -eq 'Rollback'){
    if(-not $PreviousRoot){throw 'Specify the retained previous payload directory.'}
    $candidate=[IO.Path]::GetFullPath($PreviousRoot).TrimEnd('\');Assert-PayloadPath $candidate
    if($candidate -eq $InstallRoot -or [IO.Path]::GetDirectoryName($candidate) -ne $parent){throw 'The rollback payload must be a distinct sibling directory.'}
    $manifest=Get-Content -LiteralPath (Join-Path $candidate 'payload-manifest.json') -Raw|ConvertFrom-Json
    Test-PayloadHashes $candidate $manifest
    # Apply while the old payload is still active. A policy failure leaves code
    # untouched; successful policy updates persist even if later activation fails.
    Set-SmartDriveServiceRecovery
}else{
    if(-not $SourceRoot){throw 'Supply the reviewed extracted SmartDrive source payload with SourceRoot.'}
    $SourceRoot=(Resolve-Path -LiteralPath $SourceRoot).Path;Assert-PayloadPath $SourceRoot
    $candidate=$InstallRoot+'.candidate-'+$tag
    & (Join-Path $PSScriptRoot 'Copy-AppPayload.ps1') -SourceRoot $SourceRoot -Destination $candidate -ValidateOnly
    $sourceHashes=@(Get-PayloadHashes $SourceRoot)
}
if($PSCmdlet.ShouldProcess($InstallRoot,($Action+' SmartDrive binaries while retaining service settings, account journals and the previous payload'))){
    if($Action -ne 'Rollback'){
        [void][IO.Directory]::CreateDirectory($candidate)
        & icacls.exe $candidate /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)RX'|Out-Null
        if($LASTEXITCODE){throw 'Could not protect the candidate directory.'}
        & (Join-Path $PSScriptRoot 'Copy-AppPayload.ps1') -SourceRoot $SourceRoot -Destination $candidate
        foreach($entry in $sourceHashes){
            if((Get-FileHash -LiteralPath (Join-Path $candidate $entry.path) -Algorithm SHA256).Hash -ine $entry.sha256){throw 'Candidate source copy failed checksum verification.'}
        }
        foreach($name in @('SmartDriveService','SmartDriveAccounts')){
            & $compiler /nologo /target:exe /reference:System.ServiceProcess.dll /reference:System.Web.Extensions.dll ('/out:'+(Join-Path $candidate ($name+'.exe'))) (Join-Path $candidate ('installer\windows\'+$name+'.cs'))
            if($LASTEXITCODE){throw 'Candidate service compilation failed; installed files remain unchanged.'}
        }
        if($config.office){
            & $compiler /nologo /target:exe ('/out:'+(Join-Path $candidate 'OfficePreview.exe')) (Join-Path $candidate 'installer\windows\OfficePreview.cs')
            if($LASTEXITCODE){throw 'Candidate preview runner compilation failed.'}
        }
        $manifest=@{version=1;files=@(Get-PayloadHashes $candidate -Compiled)}
        $manifest|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $candidate 'payload-manifest.json') -Encoding UTF8
    }
    # Reject syntax errors before activation. This does not start file/backup workers.
    & $settings.nodePath -e "if(typeof fetch!=='function'||typeof Headers.prototype.getSetCookie!=='function'||typeof AbortSignal.timeout!=='function'||typeof require('node:fs/promises').statfs!=='function')process.exit(1)"
    if($LASTEXITCODE){throw 'The installed Node runtime lacks required SmartDrive APIs.'}
    foreach($file in Get-ChildItem -LiteralPath @((Join-Path $candidate 'src'),(Join-Path $candidate 'web'),(Join-Path $candidate 'tools')) -File -Recurse|Where-Object {$_.Extension -in '.js','.mjs'}){
        & $settings.nodePath --check $file.FullName
        if($LASTEXITCODE){throw 'Candidate JavaScript validation failed; installed payload remains active.'}
    }
    foreach($file in Get-ChildItem -LiteralPath $candidate -Filter *.ps1 -File -Recurse){
        $tokens=$null;$errors=$null;[void][Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$errors)
        if($errors.Count){throw 'Candidate PowerShell validation failed; installed payload remains active.'}
    }
    & $settings.nodePath (Join-Path $candidate 'src\branding.js') $settings.configPath|Out-Null
    if($LASTEXITCODE){throw 'Existing installation configuration is incompatible with the candidate.'}
    $originValidator=Join-Path $candidate 'tools\validate-origin.mjs'
    if(Test-Path -LiteralPath $originValidator){
        & $settings.nodePath $originValidator $settings.configPath|Out-Null
        if($LASTEXITCODE){throw 'Existing origin TLS settings are incompatible with the candidate.'}
    }elseif($config.PSObject.Properties['originTls']){
        throw 'This candidate does not support the configured HTTPS origin. Review origin compatibility before rollback.'
    }
    # Mutable authority is copied from the CURRENT installation even on rollback.
    # Never restore old device-account ownership journals or old service secrets.
    Copy-CurrentAuthority $InstallRoot $candidate
    # Preserve an inventory for rollback of installations created before this tool.
    $oldManifest=@{version=1;files=@(Get-PayloadHashes $InstallRoot -Compiled)}
    $oldManifest|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $InstallRoot 'payload-manifest.json') -Encoding UTF8
    Test-PayloadHashes $candidate $manifest
    $journal=@{version=1;action=$Action;createdAt=[DateTime]::UtcNow.ToString('o');installRoot=$InstallRoot;candidate=$candidate;previousRoot=$retained;phase='prepared'}
    $journalPath=$InstallRoot+'.maintenance-'+$tag+'.json'
    # Parent is normally Program Files; explicitly restrict this operational journal.
    $journal|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $journalPath -Encoding UTF8
    & icacls.exe $journalPath /inheritance:r /grant:r '*S-1-5-18:F' '*S-1-5-32-544:F'|Out-Null
    if($LASTEXITCODE){throw 'Could not protect maintenance journal.'}
    Switch-ServicePayload $InstallRoot $candidate $retained
    $journal.phase='activated-services-stopped'
    $journal|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $journalPath -Encoding UTF8
    Write-Output "Payload activated; both services remain stopped. Previous payload: $retained"
    Write-Output 'Start the account service and SmartDrive for installed-client acceptance. No data, SMB shares, accounts or backup versions were replaced.'
}
