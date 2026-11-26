$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot '..\installer\windows\Publish-StudioImport.ps1'
$tokens = $null
$parseErrors = $null
$tree = [Management.Automation.Language.Parser]::ParseFile($source,[ref]$tokens,[ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
$helper = $tree.Find({param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Assert-PlainPath'
},$true)
if (-not $helper) { throw 'Missing publication path guard.' }
. ([scriptblock]::Create($helper.Extent.Text))
$root = Join-Path ([IO.Path]::GetTempPath()) ('smartdrive-paths-'+[guid]::NewGuid())
$junction = Join-Path $root 'linked'
try {
    $plain = New-Item -ItemType Directory -Path (Join-Path $root 'plain')
    $file = Join-Path $plain.FullName 'design.sstbadge'
    [IO.File]::WriteAllText($file,'{}')
    Assert-PlainPath $file
    Assert-PlainPath $plain.FullName
    $null = New-Item -ItemType Junction -Path $junction -Target $plain.FullName
    foreach ($path in @($junction,(Join-Path $junction 'design.sstbadge'))) {
        $rejected = $false
        try { Assert-PlainPath $path } catch {
            if ($_.Exception.Message -ne 'Linked import or destination path.') { throw }
            $rejected = $true
        }
        if (-not $rejected) { throw 'Publication accepted a linked path.' }
    }
    Write-Output 'Publication path checks passed.'
} finally {
    # Remove the junction itself before recursively removing our unique fixture.
    if (Test-Path -LiteralPath $junction) { [IO.Directory]::Delete($junction) }
    if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force }
}
