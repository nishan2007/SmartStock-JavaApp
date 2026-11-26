$ErrorActionPreference='Stop'
$tokens=$null;$errors=$null
$source=Join-Path $PSScriptRoot '..\tools\Rebrand-Installation.ps1'
$tree=[Management.Automation.Language.Parser]::ParseFile($source,[ref]$tokens,[ref]$errors)
if($errors.Count){throw ($errors|Out-String)}
foreach($name in @('Save-Json','Within','Assert-PlainPath')){
    $function=$tree.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name},$true)
    if(-not $function){throw "Missing rebranding helper: $name"}
    . ([scriptblock]::Create($function.Extent.Text))
}
$parent=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
$fixture=Join-Path $parent ('smartdrive-rebrand-state-'+[guid]::NewGuid())
try{
    [void][IO.Directory]::CreateDirectory($fixture)
    $old=Join-Path $fixture 'OldDrive';$new=Join-Path $fixture 'NewDrive'
    [void][IO.Directory]::CreateDirectory($old)
    $file=Join-Path $old 'design.sstbadge';[IO.File]::WriteAllText($file,'synthetic immutable file')
    Add-Type -Path (Join-Path $PSScriptRoot '..\installer\windows\IdentityStream.cs')
    $identity=[Text.Encoding]::UTF8.GetBytes('{"id":"11111111-1111-4111-8111-111111111111","storageStoreId":"1","kind":"BADGE"}')
    [SmartDriveImportIdentityStreamV1]::Write($file,$identity)
    $hash=(Get-FileHash -LiteralPath $file).Hash;$acl=(Get-Acl -LiteralPath $old).Sddl
    Assert-PlainPath $old
    if(-not (Within $fixture $old) -or -not (Within $fixture $new)){throw 'Fixture rename escaped its test directory.'}
    Rename-Item -LiteralPath $old -NewName 'NewDrive'
    $moved=Join-Path $new 'design.sstbadge'
    if((Get-FileHash -LiteralPath $moved).Hash -ne $hash -or (Get-Acl -LiteralPath $new).Sddl -ne $acl){throw 'Rename changed file bytes or directory permissions.'}
    if([Convert]::ToBase64String([SmartDriveImportIdentityStreamV1]::Read($moved)) -cne [Convert]::ToBase64String($identity)){throw 'Rename lost native identity stream.'}
    $json=Join-Path $fixture 'config.json';Save-Json $json @{root=$old};Save-Json $json @{root=$new}
    if((Get-Content -LiteralPath $json -Raw|ConvertFrom-Json).root -ne $new){throw 'Atomic configuration update failed.'}
    if(Within $new ($new+'Other')){throw 'Path containment accepted a sibling prefix.'}
    Write-Output 'Rebranding state checks passed: atomic JSON, directory ACLs, hashes and NTFS identity streams. No shares or services changed.'
}finally{
    $resolved=[IO.Path]::GetFullPath($fixture)
    if(-not $resolved.StartsWith($parent+'\',[StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetFileName($resolved) -notlike 'smartdrive-rebrand-state-*'){throw 'Unsafe fixture cleanup target.'}
    if(Test-Path -LiteralPath $resolved){Remove-Item -LiteralPath $resolved -Recurse -Force}
}
