$ErrorActionPreference = 'Stop'
$tokens=$null;$parseErrors=$null
$source=Join-Path $PSScriptRoot '..\installer\windows\Provision-Scanner.ps1'
$tree=[Management.Automation.Language.Parser]::ParseFile($source,[ref]$tokens,[ref]$parseErrors)
if($parseErrors.Count){throw ($parseErrors|Out-String)}
foreach($name in @('Save-Registry','Assert-PlainDirectory')) {
    $function=$tree.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name},$true)
    if(-not $function){throw "Missing scanner helper: $name"}
    . ([scriptblock]::Create($function.Extent.Text))
}
$parent=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
$fixture=Join-Path $parent ('smartdrive-scanner-journal-'+[guid]::NewGuid())
try {
    [void][IO.Directory]::CreateDirectory($fixture)
    Assert-PlainDirectory $fixture
    $registryPath=Join-Path $fixture 'scanner-accounts.json'
    $record=[pscustomobject]@{id='Canon-C5040';account='sdscan_test';storeId='1';ownerTag=[guid]::NewGuid().ToString('N');phase='provisioning'}
    $records=@($record)
    Save-Registry
    $first=Get-Content -LiteralPath $registryPath -Raw|ConvertFrom-Json
    if($first.version -ne 1 -or @($first.devices).Count -ne 1 -or $first.devices[0].phase -ne 'provisioning'){throw 'Initial scanner phase was not preserved.'}
    $record.phase='active';Save-Registry
    $last=Get-Content -LiteralPath $registryPath -Raw|ConvertFrom-Json
    if($last.devices[0].phase -ne 'active' -or $last.devices[0].ownerTag -ne $record.ownerTag){throw 'Scanner phase update was not preserved.'}
    if(@(Get-ChildItem -LiteralPath $fixture -Filter '*.tmp').Count){throw 'Scanner journal left temporary files.'}
    Write-Output 'Scanner provisioning journal checks passed without creating accounts or shares.'
} finally {
    $resolved=[IO.Path]::GetFullPath($fixture)
    if([IO.Path]::GetDirectoryName($resolved) -ne $parent -or [IO.Path]::GetFileName($resolved) -notmatch '^smartdrive-scanner-journal-[a-f0-9-]{36}$'){throw 'Invalid fixture cleanup path.'}
    if(Test-Path -LiteralPath $resolved){Remove-Item -LiteralPath $resolved -Recurse -Force}
}
