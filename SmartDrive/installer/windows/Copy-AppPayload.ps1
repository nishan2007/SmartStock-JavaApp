[CmdletBinding()]
param([Parameter(Mandatory)][string]$SourceRoot,[Parameter(Mandatory)][string]$Destination,[switch]$ValidateOnly)
$ErrorActionPreference = 'Stop'
$SourceRoot = (Resolve-Path -LiteralPath $SourceRoot).Path.TrimEnd('\')
if ($Destination -notmatch '^[A-Za-z]:\\') { throw 'Use an absolute local installation directory.' }
$Destination = [IO.Path]::GetFullPath($Destination).TrimEnd('\')
if ($Destination -eq $SourceRoot -or $Destination.StartsWith($SourceRoot+'\',[StringComparison]::OrdinalIgnoreCase) -or
    $SourceRoot.StartsWith($Destination+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Source and installation directories must be separate.' }
$directories = @('src','web','tools','installer\windows')
$files = @('package.json','README.md','STORAGE-SETUP.md','config.example.json','config.setup.example.json')
foreach ($item in $directories + $files + @('installer\windows\Provision-Scanner.ps1')) {
    if (-not (Test-Path -LiteralPath (Join-Path $SourceRoot $item))) { throw "Missing application payload: $item" }
}
if ((Test-Path -LiteralPath $Destination) -and @(Get-ChildItem -LiteralPath $Destination -Force).Count) {
    throw 'Choose an empty installation directory; payload copy never overwrites an installed app.'
}
if ($ValidateOnly) { return }
[void][IO.Directory]::CreateDirectory($Destination)
foreach ($folder in @('src','web')) { Copy-Item -LiteralPath (Join-Path $SourceRoot $folder) -Destination $Destination -Recurse }
$toolRoot = Join-Path $Destination 'tools'
[void][IO.Directory]::CreateDirectory($toolRoot)
Get-ChildItem -LiteralPath (Join-Path $SourceRoot 'tools') -File | Where-Object { $_.Name -notlike 'Test-*' } |
    Copy-Item -Destination $toolRoot
$installerRoot = Join-Path $Destination 'installer'
[void][IO.Directory]::CreateDirectory($installerRoot)
Copy-Item -LiteralPath (Join-Path $SourceRoot 'installer\windows') -Destination $installerRoot -Recurse
foreach ($file in $files) { Copy-Item -LiteralPath (Join-Path $SourceRoot $file) -Destination $Destination }
