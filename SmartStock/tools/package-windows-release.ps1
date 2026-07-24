param(
    [ValidateSet("development", "test", "production")]
    [string]$Environment = "production"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Target = Join-Path $Root "target"
$Release = Join-Path $Target "release-windows"
$Work = Join-Path $env:TEMP ("smartstock-windows-" + [guid]::NewGuid())

foreach ($Command in @("mvn", "jpackage")) {
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "$Command is required on the release-build computer. It is not required on the SmartStock server."
    }
}

try {
    Set-Location $Root
    & mvn -q clean package
    if ($LASTEXITCODE -ne 0) { throw "The SmartStock Maven build failed." }

    $Jar = Get-ChildItem $Target -Filter "inventory-management-*.jar" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $Jar) { throw "The packaged SmartStock JAR was not found." }
    $Version = $Jar.BaseName.Replace("inventory-management-", "")
    $InputDir = Join-Path $Work "input"
    $DependencyDir = Join-Path $InputDir "dependency"
    New-Item -ItemType Directory -Force -Path $DependencyDir | Out-Null
    Copy-Item -LiteralPath $Jar.FullName -Destination $InputDir
    Copy-Item -Path (Join-Path $Target "dependency\*") -Destination $DependencyDir -Recurse

    New-Item -ItemType Directory -Force -Path $Release | Out-Null
    & jpackage --type app-image --name SmartStock --input $InputDir `
        --main-jar $Jar.Name --main-class app.Main --dest $Work `
        --app-version $Version `
        --add-modules "java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.security.jgss,java.security.sasl,java.smartcardio,java.sql,java.transaction.xa,java.xml,jdk.httpserver,jdk.unsupported" `
        --java-options "-DSMARTSTOCK_ENVIRONMENT=$Environment"
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }

    $AppImage = Join-Path $Work "SmartStock"
    if (-not (Test-Path (Join-Path $AppImage "runtime"))) {
        throw "The Windows package was created without its bundled Java runtime."
    }
    Set-Content -LiteralPath (Join-Path $AppImage "START-SMARTSTOCK-SETUP.cmd") -Encoding ASCII -Value @(
        "@echo off",
        'start "" "%~dp0SmartStock.exe" --setup-wizard'
    )
    $Zip = Join-Path $Release "smartstock-windows-$Version.zip"
    if (Test-Path $Zip) { Remove-Item -LiteralPath $Zip -Force }
    Compress-Archive -Path $AppImage -DestinationPath $Zip -CompressionLevel Optimal
    $Hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Zip).Hash.ToLowerInvariant()
    Write-Host "Release artifact: $Zip"
    Write-Host "Version: $Version"
    Write-Host "SHA-256: $Hash"
    Write-Host "The server package includes Java. PostgreSQL is installed through Guided Setup."
} finally {
    if (Test-Path $Work) { Remove-Item -LiteralPath $Work -Recurse -Force }
}
