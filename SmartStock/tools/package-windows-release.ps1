$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Target = Join-Path $Root "target"
$Release = Join-Path $Target "release-windows"
$Work = Join-Path $env:TEMP ("smartstock-windows-" + [guid]::NewGuid())
$IconSource = Join-Path $Root "src\Images\AppIconLight.png"

function New-WindowsIcon {
    param(
        [Parameter(Mandatory = $true)][string]$PngPath,
        [Parameter(Mandatory = $true)][string]$IconPath
    )
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $IconPath) | Out-Null
    Add-Type -AssemblyName System.Drawing
    $Source = [System.Drawing.Image]::FromFile($PngPath)
    try {
        $Bitmap = New-Object System.Drawing.Bitmap 256, 256
        try {
            $Graphics = [System.Drawing.Graphics]::FromImage($Bitmap)
            try {
                $Graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $Graphics.DrawImage($Source, 0, 0, 256, 256)
            } finally {
                $Graphics.Dispose()
            }
            $PngBytes = New-Object System.IO.MemoryStream
            try {
                $Bitmap.Save($PngBytes, [System.Drawing.Imaging.ImageFormat]::Png)
                $Writer = New-Object System.IO.BinaryWriter ([System.IO.File]::Create($IconPath))
                try {
                    $Writer.Write([uint16]0)
                    $Writer.Write([uint16]1)
                    $Writer.Write([uint16]1)
                    $Writer.Write([byte]0)
                    $Writer.Write([byte]0)
                    $Writer.Write([byte]0)
                    $Writer.Write([byte]0)
                    $Writer.Write([uint16]1)
                    $Writer.Write([uint16]32)
                    $Writer.Write([uint32]$PngBytes.Length)
                    $Writer.Write([uint32]22)
                    $Writer.Write($PngBytes.ToArray())
                } finally {
                    $Writer.Dispose()
                }
            } finally {
                $PngBytes.Dispose()
            }
        } finally {
            $Bitmap.Dispose()
        }
    } finally {
        $Source.Dispose()
    }
}

foreach ($Command in @("mvn", "jpackage", "iscc")) {
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "$Command is required on the release-build computer. It is not required on the SmartStock server."
    }
}

try {
    Set-Location $Root
    New-Item -ItemType Directory -Force -Path $Work | Out-Null
    # The previous signed/setup artifact may be held open by Explorer or the
    # installer while preparing an in-place upgrade. Maven package recompiles
    # changed sources without requiring deletion of that unrelated artifact.
    & mvn -q package
    if ($LASTEXITCODE -ne 0) { throw "The SmartStock Maven build failed." }

    $Jar = Get-ChildItem $Target -Filter "inventory-management-*.jar" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $Jar) { throw "The packaged SmartStock JAR was not found." }
    $Version = $Jar.BaseName.Replace("inventory-management-", "")
    if (-not (Test-Path $IconSource)) { throw "The SmartStock Windows icon source was not found." }
    $WindowsIcon = Join-Path $Work "SmartStock.ico"
    New-WindowsIcon -PngPath $IconSource -IconPath $WindowsIcon
    $InputDir = Join-Path $Work "input"
    $DependencyDir = Join-Path $InputDir "dependency"
    New-Item -ItemType Directory -Force -Path $DependencyDir | Out-Null
    Copy-Item -LiteralPath $Jar.FullName -Destination $InputDir
    Copy-Item -Path (Join-Path $Target "dependency\*") -Destination $DependencyDir -Recurse
    $ServerLauncherProperties = Join-Path $Work "SmartStockServer.properties"
    Set-Content -LiteralPath $ServerLauncherProperties -Encoding ASCII -Value @(
        "main-jar=$($Jar.Name)",
        "main-class=app.Main",
        "arguments=--sync-service",
        "description=SmartStock LAN API and synchronization server",
        "icon=$WindowsIcon"
    )

    New-Item -ItemType Directory -Force -Path $Release | Out-Null
    & jpackage --type app-image --name SmartStock --input $InputDir `
        --main-jar $Jar.Name --main-class app.Main --dest $Work `
        --app-version $Version `
        --icon $WindowsIcon `
        --add-launcher "SmartStockServer=$ServerLauncherProperties" `
        --add-modules "java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.security.jgss,java.security.sasl,java.smartcardio,java.sql,java.transaction.xa,java.xml,jdk.crypto.ec,jdk.httpserver,jdk.unsupported"
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }

    $AppImage = Join-Path $Work "SmartStock"
    if (-not (Test-Path (Join-Path $AppImage "runtime"))) {
        throw "The Windows package was created without its bundled Java runtime."
    }
    if (-not (Test-Path (Join-Path $AppImage "SmartStockServer.exe"))) {
        throw "The Windows package was created without the named SmartStock server launcher."
    }
    # jpackage app images contain the Java runtime libraries used by the native
    # launcher but may omit java.exe itself. The in-app updater needs that
    # matching launcher to run independently after SmartStock exits.
    $JavaLauncher = (Get-Command java -ErrorAction Stop).Source
    Copy-Item -LiteralPath $JavaLauncher `
        -Destination (Join-Path $AppImage "runtime\bin\java.exe") -Force
    $JavawLauncher = Join-Path (Split-Path -Parent $JavaLauncher) "javaw.exe"
    if (-not (Test-Path -LiteralPath $JavawLauncher)) {
        throw "The matching javaw.exe launcher was not found beside java.exe."
    }
    Copy-Item -LiteralPath $JavawLauncher `
        -Destination (Join-Path $AppImage "runtime\bin\javaw.exe") -Force
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

    $InstallerScript = Join-Path $Work "SmartStock.iss"
    $InstallerBaseName = "smartstock-windows-setup-$Version"
    Set-Content -LiteralPath $InstallerScript -Encoding UTF8 -Value @"
[Setup]
AppId={{D86B7442-B5CB-4DE5-A767-16623783C468}
AppName=SmartStock
AppVersion=$Version
AppPublisher=SmartStock
DefaultDirName={autopf}\SmartStock
DefaultGroupName=SmartStock
DisableProgramGroupPage=yes
OutputDir=$Work
OutputBaseFilename=$InstallerBaseName
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\SmartStock.exe
CloseApplications=yes
RestartApplications=no
SetupLogging=yes
SetupIconFile=$WindowsIcon

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[InstallDelete]
Type: files; Name: "{app}\app\inventory-management-*.jar"

[Files]
Source: "$AppImage\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\SmartStock"; Filename: "{app}\SmartStock.exe"
Name: "{autodesktop}\SmartStock"; Filename: "{app}\SmartStock.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\SmartStock.exe"; Description: "Launch SmartStock"; Flags: nowait skipifsilent runasoriginaluser
"@

    & iscc $InstallerScript
    if ($LASTEXITCODE -ne 0) { throw "The Windows installer build failed." }
    $BuiltInstaller = Join-Path $Work "$InstallerBaseName.exe"
    if (-not (Test-Path $BuiltInstaller)) { throw "The Windows installer was not created." }
    $Installer = Join-Path $Release "$InstallerBaseName.exe"
    try {
        Copy-Item -LiteralPath $BuiltInstaller -Destination $Installer -Force -ErrorAction Stop
    } catch [System.IO.IOException] {
        $BuildStamp = Get-Date -Format "yyyyMMdd-HHmmss"
        $Installer = Join-Path $Release "$InstallerBaseName-test-$BuildStamp.exe"
        Copy-Item -LiteralPath $BuiltInstaller -Destination $Installer -Force
        Write-Warning "The normal installer filename was open in another program. Published this test build with a timestamped filename instead."
    }
    $InstallerHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Installer).Hash.ToLowerInvariant()
    Write-Host "Installer artifact: $Installer"
    Write-Host "Installer SHA-256: $InstallerHash"
    Write-Host "The server package includes Java. PostgreSQL is installed through Guided Setup."
} finally {
    if (Test-Path $Work) { Remove-Item -LiteralPath $Work -Recurse -Force }
}
