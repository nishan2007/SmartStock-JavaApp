$ErrorActionPreference='Stop'
$env:PSModulePath = "$env:WINDIR\System32\WindowsPowerShell\v1.0\Modules"
Import-Module Microsoft.PowerShell.Utility
Import-Module Microsoft.PowerShell.Archive
Add-Type -AssemblyName System.IO.Compression.FileSystem
[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskCache=Join-Path $taskRoot 'target/directml-cache'
$taskOutput=Join-Path $taskRoot 'target/native-resources/studio-directml'
New-Item -ItemType Directory -Force -Path $taskCache,$taskOutput | Out-Null
$packages=@(
    @{id='microsoft.ml.onnxruntime.directml';version='1.24.4';hash='57e9f11b73437bef7a309496135d4c1f96b1a8e9ddba60013fa27bfc1d788681'},
    @{id='microsoft.ai.directml';version='1.15.4';hash='4e7cb7ddce8cf837a7a75dc029209b520ca0101470fcdf275c1f49736a3615b9'})
foreach($package in $packages){
    $archive=Join-Path $taskCache ($package.id+'.zip')
    if(!(Test-Path $archive) -or (Get-FileHash $archive).Hash.ToLowerInvariant() -ne $package.hash){
        Invoke-WebRequest -UseBasicParsing -TimeoutSec 120 -Uri ('https://api.nuget.org/v3-flatcontainer/'+$package.id+'/'+$package.version+'/'+$package.id+'.'+$package.version+'.nupkg') -OutFile $archive
    }
    if((Get-FileHash $archive).Hash.ToLowerInvariant() -ne $package.hash){throw 'DirectML package checksum mismatch'}
    $zip=[IO.Compression.ZipFile]::OpenRead($archive)
    try {
        $entries=@{}
        foreach($notice in @('LICENSE','LICENSE.txt','LICENSE-CODE.txt','ThirdPartyNotices.txt')){
            if($zip.GetEntry($notice)){$entries[$notice]=$package.id+'-'+$notice}
        }
        if($package.id -eq 'microsoft.ai.directml'){$entries['bin/x64-win/DirectML.dll']='DirectML.dll'}
        else{$entries['runtimes/win-x64/native/onnxruntime.dll']='onnxruntime.dll'}
        foreach($entryName in $entries.Keys){
            $entry=$zip.GetEntry($entryName);if(!$entry){throw 'Pinned runtime package layout changed'}
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $taskOutput $entries[$entryName]),$true)
        }
    } finally {$zip.Dispose()}
}
Get-ChildItem $taskOutput -Filter *.dll | ForEach-Object { $_.Name+'='+ (Get-FileHash $_.FullName).Hash.ToLowerInvariant() } | Set-Content (Join-Path $taskOutput 'sha256.properties') -Encoding ASCII
