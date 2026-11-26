# Shared by maintenance tools and filesystem-only fixture tests. No entry action.
function Assert-PayloadPath([string]$Path) {
    if($Path -notmatch '^[A-Za-z]:\\'){throw 'Use an absolute local payload path.'}
    $item=Get-Item -LiteralPath $Path -Force
    while($null -ne $item){
        if($item.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Linked service payload path.'}
        $item=if($item.PSIsContainer){$item.Parent}else{$item.Directory}
    }
}
function Get-PayloadHashes([string]$Root,[switch]$Compiled) {
    Assert-PayloadPath $Root
    $Root=[IO.Path]::GetFullPath($Root).TrimEnd('\')
    $items=@()
    foreach($directory in @('src','web','tools','installer\windows')){
        $base=Join-Path $Root $directory
        if(-not (Test-Path -LiteralPath $base)){continue}
        Assert-PayloadPath $base
        $pending=New-Object 'Collections.Generic.Stack[string]';$pending.Push($base)
        while($pending.Count){foreach($item in Get-ChildItem -LiteralPath $pending.Pop() -Force){
            if($item.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Linked file in service payload.'}
            if($item.PSIsContainer){if($directory -ne 'tools'){$pending.Push($item.FullName)}}
            elseif(-not ($directory -eq 'tools' -and $item.Name -like 'Test-*')){$items+=$item.FullName}
        }}
    }
    $names=@('package.json','README.md','STORAGE-SETUP.md','config.example.json','config.setup.example.json')
    if($Compiled){$names+=@('SmartDriveService.exe','SmartDriveAccounts.exe','OfficePreview.exe')}
    foreach($name in $names){$file=Join-Path $Root $name;if(Test-Path -LiteralPath $file -PathType Leaf){Assert-PayloadPath $file;$items+=$file}}
    @($items|Sort-Object -Unique|ForEach-Object {@{path=$_.Substring($Root.Length+1);sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()}})
}
function Test-PayloadHashes([string]$Root,$Manifest) {
    if($Manifest.version -ne 1 -or $null -eq $Manifest.files){throw 'Invalid payload manifest.'}
    $actual=@(Get-PayloadHashes $Root -Compiled)
    $expected=@($Manifest.files|ForEach-Object {($_.path+'|'+$_.sha256).ToLowerInvariant()}|Sort-Object)
    $observed=@($actual|ForEach-Object {($_.path+'|'+$_.sha256).ToLowerInvariant()}|Sort-Object)
    if(($expected -join "`n") -cne ($observed -join "`n")){throw 'Service payload checksum inventory does not match.'}
}
function Switch-ServicePayload([string]$InstallRoot,[string]$Candidate,[string]$PreviousRoot) {
    $InstallRoot=[IO.Path]::GetFullPath($InstallRoot).TrimEnd('\')
    $Candidate=[IO.Path]::GetFullPath($Candidate).TrimEnd('\')
    $PreviousRoot=[IO.Path]::GetFullPath($PreviousRoot).TrimEnd('\')
    $parent=[IO.Path]::GetDirectoryName($InstallRoot)
    if($InstallRoot -eq $Candidate -or $InstallRoot -eq $PreviousRoot -or $Candidate -eq $PreviousRoot -or
        [IO.Path]::GetDirectoryName($Candidate) -ne $parent -or [IO.Path]::GetDirectoryName($PreviousRoot) -ne $parent){throw 'Service payload switches require distinct sibling directories.'}
    Assert-PayloadPath $InstallRoot;Assert-PayloadPath $Candidate
    if(Test-Path -LiteralPath $PreviousRoot){throw 'The previous-payload destination already exists.'}
    [IO.Directory]::Move($InstallRoot,$PreviousRoot)
    try{[IO.Directory]::Move($Candidate,$InstallRoot)}
    catch{
        try{[IO.Directory]::Move($PreviousRoot,$InstallRoot)}
        catch{throw 'Payload activation and restoration failed. Keep services stopped and inspect all three directories.'}
        throw 'Payload activation failed; the original installation directory was restored.'
    }
}
function Copy-CurrentAuthority([string]$InstallRoot,[string]$Candidate) {
    foreach($name in @('service.json','account-stores.json','scanner-accounts.json')){
        $source=Join-Path $InstallRoot $name;$target=Join-Path $Candidate $name
        if(Test-Path -LiteralPath $target){Assert-PayloadPath $target}
        if(Test-Path -LiteralPath $source){Assert-PayloadPath $source}
        elseif($name -ne 'scanner-accounts.json' -or (Test-Path -LiteralPath $target)){
            throw 'Current protected authority is missing. Review it instead of restoring an older account registry or service secret.'
        }
    }
    foreach($name in @('service.json','account-stores.json','scanner-accounts.json')){
        $source=Join-Path $InstallRoot $name;$target=Join-Path $Candidate $name
        if(Test-Path -LiteralPath $source){
            Copy-Item -LiteralPath $source -Destination $target -Force
            if((Get-FileHash -LiteralPath $source).Hash -ne (Get-FileHash -LiteralPath $target).Hash){throw 'Current protected state failed copy verification.'}
        }
    }
}
