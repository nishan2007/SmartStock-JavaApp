$ErrorActionPreference='Stop'
$Root=$PSScriptRoot
$State=Join-Path $Root 'status.json'
$RunId=[guid]::NewGuid().ToString()
function Status([string]$phase,[string]$message=''){
    $temp=Join-Path $Root 'status.tmp'
    @{runId=$RunId;phase=$phase;message=$message} | ConvertTo-Json -Compress | Set-Content -LiteralPath $temp -Encoding UTF8
    Move-Item -LiteralPath $temp -Destination $State -Force
}
$Installer=Join-Path $Root 'installer.exe'
try {
    Status 'CHECKING'
    $Config=Get-Content -Raw -LiteralPath (Join-Path $Root 'installation.json') | ConvertFrom-Json
    $App=[IO.Path]::GetFullPath($Config.appDirectory).TrimEnd('\')
    $Allowed=[Environment]::GetFolderPath('ProgramFiles').TrimEnd('\')+'\'
    if(-not $App.StartsWith($Allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Invalid protected installation.'}
    $node=Get-Item -LiteralPath $App
    while($node){if($node.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Unsafe installation path.'};$node=$node.Parent}
    # The installer invokes its freshly installed setup script as SYSTEM. Do not
    # allow a user-writable destination to race that script or its parent paths.
    $trusted=@('S-1-5-18','S-1-5-32-544','S-1-5-80-956008885-3418522649-1831038044-1853292631-2271478464')
    $writes=[Security.AccessControl.FileSystemRights]::Write -bor [Security.AccessControl.FileSystemRights]::Delete -bor [Security.AccessControl.FileSystemRights]::ChangePermissions -bor [Security.AccessControl.FileSystemRights]::TakeOwnership
    $items=@(Get-Item -LiteralPath $App)+@(Get-ChildItem -LiteralPath $App -Recurse -Force)
    foreach($item in $items){
        if($item.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Unsafe installation content.'}
        $acl=Get-Acl -LiteralPath $item.FullName
        if($acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -notin $trusted){throw 'The installation must be owned by Administrators or SYSTEM. Run the installer to repair it.'}
        # Read raw SIDs so orphaned/domain accounts do not require name lookup.
        # Unknown principals with write access must still fail the trust check.
        foreach($rule in $acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])){
            if($rule.PropagationFlags -band [Security.AccessControl.PropagationFlags]::InheritOnly){continue}
            $identity=$rule.IdentityReference.Value
            if($rule.AccessControlType -eq 'Allow' -and ($rule.FileSystemRights -band $writes) -and $identity -notin $trusted){throw 'The installation is writable by an untrusted principal.'}
        }
    }
    [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
    # Only fixed official HTTPS endpoints. No user-supplied commands or paths.
    $Metadata=Invoke-RestMethod -Uri 'https://downloads.deckers.gy/manifest/windows' -MaximumRedirection 0 -TimeoutSec 30
    if($Metadata.platform -ne 'windows' -or $Metadata.version -notmatch '^\d+\.\d+\.\d+$' -or $Metadata.sha256 -notmatch '^[a-f0-9]{64}$' -or [long]$Metadata.size -lt 1 -or [long]$Metadata.size -gt 2147483648){throw 'Invalid official metadata.'}
    $Parts=$Metadata.version.Split('.')
    if([long]$Metadata.buildNumber -ne ([long]$Parts[0]*100000+[long]$Parts[1]*1000+[long]$Parts[2])){throw 'Invalid build number.'}
    # Follow the launcher's classpath, not leftover JARs from another version.
    $Launcher=Get-Content -LiteralPath (Join-Path $App 'app\SmartStock.cfg')
    $Versions=@($Launcher | ForEach-Object {if($_ -match '^app\.classpath=\$APPDIR[\\/]inventory-management-(\d+\.\d+\.\d+)\.jar\s*$'){[version]$Matches[1]}})
    if($Versions.Count -ne 1){throw 'Installed launcher version is ambiguous or missing.'}
    $Current=$Versions[0]
    if(-not $Current){throw 'Installed version not found.'}
    if([version]$Metadata.version -le $Current){Status 'CURRENT' ('Installed '+$Current+'; latest installer '+$Metadata.version);exit}
    Status 'DOWNLOADING'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://downloads.deckers.gy/download/windows' -MaximumRedirection 0 -OutFile $Installer -TimeoutSec 1800
    if((Get-Item -LiteralPath $Installer).Length -ne [long]$Metadata.size -or (Get-FileHash -LiteralPath $Installer -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Metadata.sha256){throw 'Installer size or SHA-256 mismatch.'}
    Status 'READY'
    Start-Sleep -Seconds 15
    Status 'INSTALLING'
    $Process=Start-Process -FilePath $Installer -ArgumentList @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART','/NOICONS','/CLOSEAPPLICATIONS',('/DIR="'+$App+'"'),('/LOG="'+(Join-Path $Root 'installer.log')+'"')) -Wait -PassThru -WindowStyle Hidden
    if($Process.ExitCode -notin @(0,3010)){throw ('Installer failed: '+$Process.ExitCode)}
    if(-not (Test-Path -LiteralPath (Join-Path $App ('app\inventory-management-'+$Metadata.version+'.jar')))){throw 'Updated application missing.'}
    $ExpectedClasspath='app.classpath=$APPDIR\inventory-management-'+$Metadata.version+'.jar'
    if($ExpectedClasspath -notin @(Get-Content -LiteralPath (Join-Path $App 'app\SmartStock.cfg'))){throw 'Updated launcher does not select the installed release.'}
    Status 'COMPLETE'
}catch{Status 'FAILED' $_.Exception.Message;exit 1}
finally{if(Test-Path -LiteralPath $Installer){Remove-Item -LiteralPath $Installer -Force}}
