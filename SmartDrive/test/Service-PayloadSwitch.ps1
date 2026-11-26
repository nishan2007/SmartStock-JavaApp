$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '..\installer\windows\Service-Payload.ps1')
$parent=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
$fixture=Join-Path $parent ('smartdrive-payload-switch-'+[guid]::NewGuid())
function New-FixturePayload([string]$Path,[string]$Code){
    [void][IO.Directory]::CreateDirectory((Join-Path $Path 'src'))
    [IO.File]::WriteAllText((Join-Path $Path 'src\main.js'),$Code)
    [IO.File]::WriteAllText((Join-Path $Path 'package.json'),'{"name":"smartdrive"}')
}
try{
    [void][IO.Directory]::CreateDirectory($fixture)
    $current=Join-Path $fixture 'SmartDrive';$candidate=Join-Path $fixture 'candidate';$previous=Join-Path $fixture 'previous'
    New-FixturePayload $current 'export const version=1;';New-FixturePayload $candidate 'export const version=2;'
    [IO.File]::WriteAllText((Join-Path $current 'service.json'),'{"syntheticCredential":"current"}')
    [IO.File]::WriteAllText((Join-Path $current 'account-stores.json'),'{"storeIds":["1"]}')
    [IO.File]::WriteAllText((Join-Path $current 'scanner-accounts.json'),'{"generation":2}')
    [IO.File]::WriteAllText((Join-Path $candidate 'scanner-accounts.json'),'{"generation":1}')
    $manifest=@{version=1;files=@(Get-PayloadHashes $candidate -Compiled)}
    Test-PayloadHashes $candidate $manifest
    Copy-CurrentAuthority $current $candidate
    Test-PayloadHashes $candidate $manifest
    Switch-ServicePayload $current $candidate $previous
    if([IO.File]::ReadAllText((Join-Path $current 'src\main.js')) -notlike '*version=2*'){throw 'Candidate did not activate.'}
    if((Get-Content -LiteralPath (Join-Path $current 'scanner-accounts.json') -Raw|ConvertFrom-Json).generation -ne 2){throw 'Activation lost current scanner authority.'}
    [IO.File]::WriteAllText((Join-Path $current 'scanner-accounts.json'),'{"generation":3}')
    Copy-CurrentAuthority $current $previous
    Switch-ServicePayload $current $previous (Join-Path $fixture 'retained-newer')
    if([IO.File]::ReadAllText((Join-Path $current 'src\main.js')) -notlike '*version=1*'){throw 'Previous code did not activate.'}
    if((Get-Content -LiteralPath (Join-Path $current 'scanner-accounts.json') -Raw|ConvertFrom-Json).generation -ne 3){throw 'Rollback restored stale scanner authority.'}
    if((Get-Content -LiteralPath (Join-Path $current 'service.json') -Raw|ConvertFrom-Json).syntheticCredential -ne 'current'){throw 'Rollback changed current protected settings.'}
    $scannerPath=Join-Path $current 'scanner-accounts.json'
    $authority=[IO.File]::ReadAllText($scannerPath);Remove-Item -LiteralPath $scannerPath
    $rejected=$false;try{Copy-CurrentAuthority $current (Join-Path $fixture 'retained-newer')}catch{$rejected=$true}
    if(-not $rejected){throw 'Rollback attempted to resurrect an account registry missing from current authority.'}
    [IO.File]::WriteAllText($scannerPath,$authority)
    $manifest=@{version=1;files=@(Get-PayloadHashes $current -Compiled)}
    [IO.File]::AppendAllText((Join-Path $current 'src\main.js'),'tampered')
    $rejected=$false;try{Test-PayloadHashes $current $manifest}catch{$rejected=$true}
    if(-not $rejected){throw 'Payload corruption was accepted.'}
    Write-Output 'Payload switching checks passed: code activation/rollback, current authority preservation and checksum rejection. No installed services or shares changed.'
}finally{
    $resolved=[IO.Path]::GetFullPath($fixture)
    if(-not $resolved.StartsWith($parent+'\',[StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetFileName($resolved) -notlike 'smartdrive-payload-switch-*'){throw 'Unsafe fixture cleanup target.'}
    if(Test-Path -LiteralPath $resolved){Remove-Item -LiteralPath $resolved -Recurse -Force}
}
