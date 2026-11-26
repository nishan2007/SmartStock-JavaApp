[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$SourcePath,
    [Parameter(Mandatory)][string]$WorkingRoot,
    [Parameter(Mandatory)][string]$ClassPath,
    [string]$JavaPath='java.exe',
    [string]$NodePath='node.exe',
    [switch]$VerifyPreparedCopy
)
$ErrorActionPreference='Stop'
if($WorkingRoot -notmatch '^[A-Za-z]:\\' -or (-not $VerifyPreparedCopy -and (Test-Path -LiteralPath $WorkingRoot))){throw 'Choose a new absolute private rehearsal directory outside the repository and live library.'}
$WorkingRoot=[IO.Path]::GetFullPath($WorkingRoot)
$source=(Resolve-Path -LiteralPath $SourcePath).Path.TrimEnd('\')
$repository=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path.TrimEnd('\')
foreach($forbidden in @($source,$repository)){
    if($WorkingRoot -eq $forbidden -or $WorkingRoot.StartsWith($forbidden+'\',[StringComparison]::OrdinalIgnoreCase) -or $forbidden.StartsWith($WorkingRoot.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Rehearsal evidence must stay separate from the live library and repository.'}
}
$parent=Get-Item -LiteralPath $(if($VerifyPreparedCopy){$WorkingRoot}else{[IO.Path]::GetDirectoryName($WorkingRoot)}) -Force
while($null -ne $parent){if($parent.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Linked rehearsal parent.'};$parent=$parent.Parent}
if($VerifyPreparedCopy -and -not (Test-Path -LiteralPath (Join-Path $WorkingRoot 'rehearsal-report.json') -PathType Leaf)){throw 'The retained rehearsal report is missing.'}
if(-not $VerifyPreparedCopy){[void][IO.Directory]::CreateDirectory($WorkingRoot)}
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $WorkingRoot /inheritance:r /grant:r ('*'+$sid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F'|Out-Null
if($LASTEXITCODE){throw 'Could not protect rehearsal evidence. No library files have been copied.'}
$arguments=@((Join-Path $PSScriptRoot 'rehearse-library.mjs'),$source,$WorkingRoot,$JavaPath,$ClassPath)
if($VerifyPreparedCopy){$arguments+='--verify-prepared-copy'}
& $NodePath @arguments
if($LASTEXITCODE){throw 'Rehearsal did not pass. Evidence is retained privately for review; no live cutover occurred.'}
