param(
    [Parameter(Mandatory)][string]$Source,
    [Parameter(Mandatory)][string]$Destination
)
$ErrorActionPreference = 'Stop'
$inputFile = [IO.File]::Open($Source, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::None)
try {
    if ($inputFile.Length -gt 50MB) {
        throw 'Scan exceeds the configured intake limit.'
    }
    $outputFile = [IO.File]::Open($Destination, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $inputFile.CopyTo($outputFile)
        $outputFile.Flush($true)
    } finally {
        $outputFile.Dispose()
    }
} finally {
    $inputFile.Dispose()
}
