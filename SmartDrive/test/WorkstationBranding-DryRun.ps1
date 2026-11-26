$ErrorActionPreference='Stop'
$tag=[guid]::NewGuid().ToString('N').Substring(0,16)
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('smartdrive-workstation-branding-'+$tag+'.json')
$old='FixtureOld-'+$tag;$new='FixtureNew-'+$tag
function Get-SmbMapping { [pscustomobject]@{RemotePath=('\\FIXTURE\'+$old);LocalPath='X:'} }
function Get-SmbConnection {}
function Remove-SmbMapping { throw 'Dry run attempted to remove a mapping.' }
function New-SmbMapping { throw 'Dry run attempted to create a mapping.' }
try{
    @{server='FIXTURE';oldShare=$old;newShare=$new;displayName='Fixture Drive';url='https://drive.example/employees/drive/'}|
        ConvertTo-Json|Set-Content -LiteralPath $fixture -Encoding UTF8
    & (Join-Path $PSScriptRoot '..\tools\Update-WorkstationBranding.ps1') -SettingsPath $fixture -WhatIf
    $desktop=[Environment]::GetFolderPath('Desktop')
    if((Test-Path -LiteralPath (Join-Path $desktop ($new+' Management.lnk')))){throw 'Dry run created a shortcut.'}
    Write-Output 'Workstation branding dry run passed without changing credentials, mappings or shortcuts.'
}finally{if(Test-Path -LiteralPath $fixture){Remove-Item -LiteralPath $fixture}}
