param([string]$OutputDirectory, [switch]$RegisterOnly, [switch]$Publish, [switch]$UpdateOnly)
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Target = Join-Path $Root "target"
$Release = if ([string]::IsNullOrWhiteSpace($OutputDirectory)) { Join-Path $Target "release-windows" } else { [System.IO.Path]::GetFullPath($OutputDirectory) }
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

$RequiredCommands = if ($UpdateOnly) { @("mvn", "jar") } else { @("mvn", "jar", "jpackage", "iscc") }
foreach ($Command in $RequiredCommands) {
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
    # Standalone JDK-only migration bridge for installations with older native updaters.
    New-Item -ItemType Directory -Force -Path $Release | Out-Null
    & jar --create --file (Join-Path $Release 'smartstock-ai-model-migration.jar') --main-class app.StudioModelMigration -C (Join-Path $Target 'classes') app/StudioModelMigration.class
    if ($LASTEXITCODE -ne 0) { throw 'Could not package the standalone model migration bridge.' }
    $MigrationBridge = Join-Path $Release 'smartstock-ai-model-migration.jar'
    $MigrationHash = (Get-FileHash -LiteralPath $MigrationBridge -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath "$MigrationBridge.sha256" -Encoding ASCII -Value "$MigrationHash  smartstock-ai-model-migration.jar"
    if (-not $UpdateOnly) {
    if (-not (Test-Path $IconSource)) { throw "The SmartStock Windows icon source was not found." }
    $WindowsIcon = Join-Path $Work "SmartStock.ico"
    New-WindowsIcon -PngPath $IconSource -IconPath $WindowsIcon
    }
    $InputDir = Join-Path $Work "input"
    $DependencyDir = Join-Path $InputDir "dependency"
    New-Item -ItemType Directory -Force -Path $DependencyDir | Out-Null
    Copy-Item -LiteralPath $Jar.FullName -Destination $InputDir
    Copy-Item -Path (Join-Path $Target "dependency\*") -Destination $DependencyDir -Recurse
    # Models live in the store profile and are downloaded independently.
    $StudioDir = Join-Path $DependencyDir 'catalog-studio'
    Get-ChildItem -LiteralPath $DependencyDir -Recurse -File -Filter '*.onnx' | ForEach-Object {
        Remove-Item -LiteralPath $_.FullName -Force
    }
    & (Join-Path $PSScriptRoot 'stage-studio-models.ps1') -Destination $StudioDir -NoticesOnly
    # Both the app image and updater ZIP carry the same pinned tunnel executable.
    & (Join-Path $PSScriptRoot 'stage-cloudflared.ps1') -Destination (Join-Path $DependencyDir 'cloudflared\windows-amd64')
    New-Item -ItemType Directory -Force -Path $Release | Out-Null
    if (-not $UpdateOnly) {
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
    # Server setup must work when the store computer has no usable winget source.
    if (-not $RegisterOnly) {
    # Pin the EDB installer to the SHA-256 published in the Windows Package
    # Manager manifest for PostgreSQL.PostgreSQL.17 version 17.11-4.
    $PostgresInstallerUrl = 'https://get.enterprisedb.com/postgresql/postgresql-17.11-4-windows-x64.exe'
    $PostgresInstallerHash = 'c9828fd3a4daebbeeace19bec2de5f38d73c047fce47278148b525cfbe28a5e4'
    $PostgresInstallerCache = Join-Path $env:TEMP 'smartstock-postgresql-17.11-4-windows-x64.exe'
    if (-not (Test-Path -LiteralPath $PostgresInstallerCache) -or
        (Get-FileHash -LiteralPath $PostgresInstallerCache -Algorithm SHA256).Hash.ToLowerInvariant() -ne $PostgresInstallerHash) {
        Invoke-WebRequest -Uri $PostgresInstallerUrl -OutFile $PostgresInstallerCache
    }
    if ((Get-FileHash -LiteralPath $PostgresInstallerCache -Algorithm SHA256).Hash.ToLowerInvariant() -ne $PostgresInstallerHash) {
        throw 'The PostgreSQL installer failed its SHA-256 check.'
    }
    $BundledPostgresInstaller = Join-Path $AppImage 'postgresql-installer.exe'
    Copy-Item -LiteralPath $PostgresInstallerCache -Destination $BundledPostgresInstaller
    if ((Get-FileHash -LiteralPath $BundledPostgresInstaller -Algorithm SHA256).Hash.ToLowerInvariant() -ne $PostgresInstallerHash) {
        throw 'The packaged PostgreSQL installer failed its SHA-256 check.'
    }
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
    }
    # The in-app updater installs only the application JAR and dependency
    # directory. Keep both at the archive root; a ZIP of the complete jpackage
    # image nests them under SmartStock\app and cannot be applied by the
    # currently installed updater.
    $Zip = Join-Path $Release "smartstock-windows-$Version.zip"
    if (Test-Path $Zip) { Remove-Item -LiteralPath $Zip -Force }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.IO.Compression
    # Write portable ZIP names explicitly. Compress-Archive uses backslashes
    # for directories, which Java ZipEntry does not recognize as directories.
    $OutputArchive = [System.IO.Compression.ZipFile]::Open($Zip, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        $InputRoot = (Resolve-Path -LiteralPath $InputDir).Path.TrimEnd('\') + '\'
        Get-ChildItem -LiteralPath $InputDir -File -Recurse | ForEach-Object {
            $EntryName = $_.FullName.Substring($InputRoot.Length).Replace('\', '/')
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $OutputArchive, $_.FullName, $EntryName,
                [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally {
        $OutputArchive.Dispose()
    }
    $Archive = [System.IO.Compression.ZipFile]::OpenRead($Zip)
    try {
        $ArchiveNames = @($Archive.Entries | ForEach-Object { $_.FullName })
        if ($ArchiveNames | Where-Object { $_.Contains('\') }) {
            throw 'The updater archive contains non-portable backslash entry names.'
        }
        if ($ArchiveNames -notcontains $Jar.Name) {
            throw "The Windows updater archive does not contain $($Jar.Name) at its root."
        }
        if (-not ($ArchiveNames | Where-Object { $_ -like "dependency/*.jar" })) {
            throw "The Windows updater archive does not contain root-level dependencies."
        }
        if ($ArchiveNames -notcontains 'dependency/cloudflared/windows-amd64/cloudflared.exe') {
            throw 'The updater archive is missing its pinned Cloudflare client.'
        }
        if ($ArchiveNames -notcontains 'dependency/catalog-studio/catalog-studio-model-NOTICE.txt' -or
            $ArchiveNames -notcontains 'dependency/catalog-studio/birefnet-LICENSE.txt' -or
            -not ($ArchiveNames | Where-Object { $_ -like 'dependency/onnxruntime-*.jar' })) {
            throw 'The updater archive is missing the built-in catalog photo processor.'
        }
        if ($ArchiveNames | Where-Object { $_ -like '*.onnx' }) {
            throw 'AI models must be published separately from application updates.'
        }
        if ($ArchiveNames | Where-Object { $_ -like "SmartStock/*" }) {
            throw "The Windows updater archive contains an incompatible nested SmartStock app image."
        }
    } finally {
        $Archive.Dispose()
    }
    $Hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Zip).Hash.ToLowerInvariant()
    Set-Content -LiteralPath "$Zip.sha256" -Encoding ASCII -Value "$Hash  $([IO.Path]::GetFileName($Zip))"
    Write-Host "Release artifact: $Zip"
    Write-Host "Version: $Version"
    Write-Host "SHA-256: $Hash"

    if ($UpdateOnly) {
        Write-Host "Update-only package: no installer was created."
        if ($Publish) {
            $VersionParts = $Version.Split('.')
            $ReleaseBuildNumber = [int]$VersionParts[0] * 100000 + [int]$VersionParts[1] * 1000 + [int]$VersionParts[2]
            $NotesPath = Join-Path $Root "release-notes-$Version.txt"
            if (-not (Test-Path -LiteralPath $NotesPath)) { throw 'Release notes are required to publish this build.' }
            & (Join-Path $PSScriptRoot 'publish-r2-update-windows.ps1') -Artifact $Zip -Version $Version -BuildNumber $ReleaseBuildNumber -ReleaseNotes $NotesPath
        }
        return
    }

    $InstallerScript = Join-Path $Work "SmartStock.iss"
    $InstallerBaseName = if ($RegisterOnly) { "smartstock-windows-register-setup-$Version" } else { "smartstock-windows-setup-$Version" }
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
PrivilegesRequiredOverridesAllowed=commandline
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\SmartStock.exe
CloseApplications=yes
RestartApplications=no
SetupLogging=yes
SetupIconFile=$WindowsIcon

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[Files]
Source: "$AppImage\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\SmartStock"; Filename: "{app}\SmartStock.exe"
Name: "{autodesktop}\SmartStock"; Filename: "{app}\SmartStock.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\SmartStock.exe"; Description: "Launch SmartStock"; Flags: nowait skipifsilent runasoriginaluser

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
var
  FindRec: TFindRec;
  Candidate: String;
begin
  { Delete superseded JARs only after the complete new payload is installed. }
  if CurStep = ssPostInstall then
  begin
    if FindFirst(ExpandConstant('{app}\app\inventory-management-*.jar'), FindRec) then
    begin
      try
        repeat
          if CompareText(FindRec.Name, '$($Jar.Name)') <> 0 then
          begin
            Candidate := ExpandConstant('{app}\app\') + FindRec.Name;
            DeleteFile(Candidate);
          end;
        until not FindNext(FindRec);
      finally
        FindClose(FindRec);
      end;
    end;
  end;
end;
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
    Set-Content -LiteralPath "$Installer.sha256" -Encoding ASCII -Value "$InstallerHash  $([IO.Path]::GetFileName($Installer))"
    if ($Publish) {
        $VersionParts = $Version.Split('.')
        $ReleaseBuildNumber = [int]$VersionParts[0] * 100000 + [int]$VersionParts[1] * 1000 + [int]$VersionParts[2]
        $NotesPath = Join-Path $Root "release-notes-$Version.txt"
        if (-not (Test-Path -LiteralPath $NotesPath)) { throw 'Release notes are required to publish this build.' }
        & (Join-Path $PSScriptRoot 'publish-r2-update-windows.ps1') -Artifact $Zip -Version $Version -BuildNumber $ReleaseBuildNumber -ReleaseNotes $NotesPath -Installer $Installer
    }
    if ($RegisterOnly) {
        Write-Host "The register installer includes Java and omits the PostgreSQL server installer."
    } else {
        Write-Host "The all-in-one installer includes Java and a verified PostgreSQL installer for server setup."
    }

} finally {
    $ResolvedWork = [System.IO.Path]::GetFullPath($Work)
    $TempRoot = [System.IO.Path]::GetFullPath($env:TEMP).TrimEnd('\') + '\'
    if (-not $ResolvedWork.StartsWith($TempRoot, [StringComparison]::OrdinalIgnoreCase) -or
        -not ([System.IO.Path]::GetFileName($ResolvedWork) -match '^smartstock-windows-[a-f0-9-]{36}$')) {
        throw 'Refusing cleanup outside the verified packaging temporary directory.'
    }
    if (Test-Path -LiteralPath $ResolvedWork) { Remove-Item -LiteralPath $ResolvedWork -Recurse -Force }
}
