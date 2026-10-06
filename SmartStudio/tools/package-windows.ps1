param([string]$RuntimePath, [switch]$Installer)
$ErrorActionPreference = 'Stop'
$studioRoot = Split-Path $PSScriptRoot -Parent
$studioTarget = Join-Path $studioRoot 'target'
[xml]$studioPom = Get-Content -LiteralPath (Join-Path $studioRoot 'pom.xml')
$studioVersion=$studioPom.project.version
$studioJarName="smartstudio-$studioVersion.jar"
$studioJar = Join-Path $studioTarget $studioJarName
if (!(Test-Path -LiteralPath $studioJar)) { throw 'Build SmartStudio before packaging.' }
if (!$RuntimePath) {
    $RuntimePath = Join-Path (Split-Path $studioRoot -Parent) 'SmartStock/target/offline-clean-bundle/stage/SmartStock/runtime'
}
if (!(Test-Path -LiteralPath (Join-Path $RuntimePath 'bin/javaw.exe'))) { throw 'Provide a Java 17 Windows runtime with -RuntimePath.' }
$studioOutput = Join-Path $studioTarget ('portable-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $studioOutput | Out-Null
$studioInput = Join-Path $studioTarget ('package-input-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $studioInput | Out-Null
Copy-Item -LiteralPath $studioJar -Destination $studioInput
$studioServerVersion = ($studioPom.project.dependencies.dependency | Where-Object { $_.artifactId -eq 'inventory-management' }).version
$studioLibraries = Join-Path $studioInput 'lib'
New-Item -ItemType Directory -Path $studioLibraries | Out-Null
# Maven may retain dependencies from a previous build; package only the selected server client.
Get-ChildItem -LiteralPath (Join-Path $studioTarget 'lib') -File | Where-Object {
    $_.Name -notlike 'inventory-management-*.jar' -or $_.Name -eq "inventory-management-$studioServerVersion.jar"
} | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $studioLibraries }
# Use SmartStudio's own multi-size paintbrush icon for the executable and shortcuts.
$studioIcon = Join-Path $studioRoot 'src/main/resources/gy/deckers/studio/icons/SmartStudio.ico'
if (!(Test-Path -LiteralPath $studioIcon)) { throw 'Generate SmartStudio icons with tools/GenerateStudioIcons.java before packaging.' }
& jpackage --type app-image --name SmartStudio --app-version $studioVersion --input $studioInput --main-jar $studioJarName --main-class gy.deckers.studio.SmartStudio --runtime-image $RuntimePath --dest $studioOutput --icon $studioIcon
if ($LASTEXITCODE -ne 0) { throw 'SmartStudio packaging failed.' }
$studioPreviewPath = Join-Path $studioTarget 'login-preview.png'
$studioLaunch = Start-Process -FilePath (Join-Path $studioOutput 'SmartStudio/SmartStudio.exe') -ArgumentList "--check-launch `"$studioPreviewPath`"" -WindowStyle Hidden -PassThru
if (!$studioLaunch.WaitForExit(30000) -or $studioLaunch.ExitCode -ne 0 -or !(Test-Path -LiteralPath $studioPreviewPath)) { throw 'The packaged SmartStudio launcher did not pass its startup check.' }
$studioZip = Join-Path $studioTarget ("SmartStudio-$studioVersion-windows-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip')
Compress-Archive -LiteralPath (Join-Path $studioOutput 'SmartStudio') -DestinationPath $studioZip
Get-Item -LiteralPath $studioZip | Select-Object FullName,Length
(Get-FileHash -LiteralPath $studioZip -Algorithm SHA256).Hash
if ($Installer) {
    if (!(Get-Command iscc -ErrorAction SilentlyContinue)) { throw 'Inno Setup is required to build the installer.' }
    $studioInstallerScript = Join-Path $studioOutput 'SmartStudio.iss'
    $studioImage = Join-Path $studioOutput 'SmartStudio'
    $studioInstallerName = "SmartStudio-setup-$studioVersion"
    $studioSetup = @"
[Setup]
AppId={{AAC74691-2C85-4B6C-94B1-ABDBA15DA407}
AppName=SmartStudio
AppVersion=$studioVersion
AppPublisher=Deckers
DefaultDirName={localappdata}\Programs\SmartStudio
DefaultGroupName=SmartStudio
DisableProgramGroupPage=yes
OutputDir=$studioTarget
OutputBaseFilename=$studioInstallerName
Compression=lzma2/fast
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\SmartStudio.exe
SetupIconFile=$studioIcon
CloseApplications=yes
RestartApplications=no
SetupLogging=yes

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"

[Files]
Source: "$studioImage\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\SmartStudio"; Filename: "{app}\SmartStudio.exe"
Name: "{userdesktop}\SmartStudio"; Filename: "{app}\SmartStudio.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\SmartStudio.exe"; Description: "Open SmartStudio"; Flags: nowait skipifsilent
Filename: "{app}\SmartStudio.exe"; Flags: nowait; Check: ShouldReopenStudio

[Code]
function ShouldReopenStudio: Boolean;
var
  Index: Integer;
begin
  Result := False;
  if not WizardSilent then Exit;
  for Index := 1 to ParamCount do
    if (CompareText(ParamStr(Index), '/SILENT') = 0) or
       (CompareText(ParamStr(Index), '/REOPENSTUDIO') = 0) then
      Result := True;
end;

[InstallDelete]
Type: files; Name: "{app}\app\smartstudio-*.jar"
Type: files; Name: "{app}\app\lib\inventory-management-*.jar"
"@
    [IO.File]::WriteAllText($studioInstallerScript,$studioSetup,[Text.UTF8Encoding]::new($false))
    & iscc /Q $studioInstallerScript
    if ($LASTEXITCODE -ne 0) { throw 'SmartStudio installer build failed.' }
    $studioInstallerFile = Join-Path $studioTarget ($studioInstallerName+'.exe')
    Get-Item -LiteralPath $studioInstallerFile | Select-Object FullName,Length
    (Get-FileHash -LiteralPath $studioInstallerFile -Algorithm SHA256).Hash
}
