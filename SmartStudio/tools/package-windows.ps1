param([string]$RuntimePath, [switch]$Installer, [switch]$UpdateOnly, [string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
if($UpdateOnly){$Installer=$true}
$studioRoot = Split-Path $PSScriptRoot -Parent
$studioTarget = Join-Path $studioRoot 'target'
$studioBuildTarget=$studioTarget
if($OutputDirectory){$studioTarget=[IO.Path]::GetFullPath($OutputDirectory);New-Item -ItemType Directory -Path $studioTarget -Force | Out-Null}
[xml]$studioPom = Get-Content -LiteralPath (Join-Path $studioRoot 'pom.xml')
$studioVersion=$studioPom.project.version
$studioJarName="smartstudio-$studioVersion.jar"
$studioJar = Join-Path $studioBuildTarget $studioJarName
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
# Maven retains obsolete JARs in target/lib. Copy only this build's resolved
# manifest classpath, especially when the PDF engine changes dependency versions.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$studioArchive=[IO.Compression.ZipFile]::OpenRead($studioJar)
try {
    $studioManifestReader=[IO.StreamReader]::new($studioArchive.GetEntry('META-INF/MANIFEST.MF').Open())
    try {$studioManifest=$studioManifestReader.ReadToEnd() -replace '\r?\n ',''} finally {$studioManifestReader.Dispose()}
} finally {$studioArchive.Dispose()}
$studioClasspath=[regex]::Match($studioManifest,'(?m)^Class-Path: ([^\r\n]+)').Groups[1].Value
if(!$studioClasspath){throw 'The packaged Studio JAR has no resolved dependency classpath.'}
$studioResolved=@($studioClasspath.Split(' ',[StringSplitOptions]::RemoveEmptyEntries))
if(@($studioResolved | Where-Object {$_ -like 'lib/inventory-management-*.jar'}).Count -ne 1 -or $studioResolved -notcontains "lib/inventory-management-$studioServerVersion.jar") {throw 'Manifest does not select the expected SmartStock client dependency.'}
foreach($studioDependency in $studioResolved) {
    if($studioDependency -notmatch '^lib/[A-Za-z0-9_.+-]+\.jar$'){throw 'Unsafe dependency path in Studio manifest.'}
    Copy-Item -LiteralPath (Join-Path $studioBuildTarget $studioDependency) -Destination $studioLibraries
}
# Model weights are separate, but their redistribution notices remain with the runtime.
$studioModelNotices = Join-Path $studioLibraries 'catalog-studio'
New-Item -ItemType Directory -Path $studioModelNotices | Out-Null
foreach($notice in @('catalog-studio-model-NOTICE.txt','birefnet-LICENSE.txt')) {
    Copy-Item -LiteralPath (Join-Path (Split-Path $studioRoot -Parent) "SmartStock/tools/$notice") -Destination $studioModelNotices
}
# Use SmartStudio's own multi-size paintbrush icon for the executable and shortcuts.
$studioIcon = Join-Path $studioRoot 'src/main/resources/gy/deckers/studio/icons/SmartStudio.ico'
if (!(Test-Path -LiteralPath $studioIcon)) { throw 'Generate SmartStudio icons with tools/GenerateStudioIcons.java before packaging.' }
& jpackage --type app-image --name SmartStudio --app-version $studioVersion --input $studioInput --main-jar $studioJarName --main-class gy.deckers.studio.SmartStudio --runtime-image $RuntimePath --dest $studioOutput --icon $studioIcon
if ($LASTEXITCODE -ne 0) { throw 'SmartStudio packaging failed.' }
$studioShellCompiler=Join-Path $env:SystemRoot 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if(!(Test-Path -LiteralPath $studioShellCompiler)){throw 'The Windows .NET Framework compiler is required for Explorer previews.'}
$studioShellDll=Join-Path $studioOutput 'SmartStudio\SmartStudioShellPreview.dll'
& $studioShellCompiler /nologo /target:library /reference:System.Drawing.dll /reference:System.Windows.Forms.dll /reference:System.Web.Extensions.dll /reference:System.IO.Compression.dll ("/out:"+$studioShellDll) (Join-Path $studioRoot 'installer\windows\StudioShellPreview.cs')
if($LASTEXITCODE){throw 'SmartStudio Explorer preview compilation failed.'}
$studioPreviewPath = Join-Path $studioTarget 'login-preview.png'
$studioLaunch = Start-Process -FilePath (Join-Path $studioOutput 'SmartStudio/SmartStudio.exe') -ArgumentList "--check-launch `"$studioPreviewPath`"" -WindowStyle Hidden -PassThru
if (!$studioLaunch.WaitForExit(30000) -or $studioLaunch.ExitCode -ne 0 -or !(Test-Path -LiteralPath $studioPreviewPath)) { throw 'The packaged SmartStudio launcher did not pass its startup check.' }
if(!$UpdateOnly){
    $studioZip = Join-Path $studioTarget ("SmartStudio-$studioVersion-windows-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip')
    Compress-Archive -LiteralPath (Join-Path $studioOutput 'SmartStudio') -DestinationPath $studioZip
    Get-Item -LiteralPath $studioZip | Select-Object FullName,Length
    (Get-FileHash -LiteralPath $studioZip -Algorithm SHA256).Hash
}
if ($Installer) {
    if (!(Get-Command iscc -ErrorAction SilentlyContinue)) { throw 'Inno Setup is required to build the installer.' }
    $studioInstallerScript = Join-Path $studioOutput 'SmartStudio.iss'
    $studioImage = Join-Path $studioOutput 'SmartStudio'
    $studioInstallerName = if($UpdateOnly){"SmartStudio-update-$studioVersion"}else{"SmartStudio-setup-$studioVersion"}
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
ChangesAssociations=yes
#include "$studioRoot\installer\windows\StudioShellPreview.iss"

[Registry]
Root: HKA; Subkey: "Software\Classes\.sspub"; ValueType: string; ValueData: "SmartStudio.Publication"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\.sstbadge"; ValueType: string; ValueData: "SmartStudio.Badge"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\.sspub\OpenWithProgids"; ValueType: string; ValueName: "SmartStudio.Publication"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\.sstbadge\OpenWithProgids"; ValueType: string; ValueName: "SmartStudio.Badge"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\SmartStudio.Publication"; ValueType: string; ValueData: "SmartStudio publication"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\SmartStudio.Publication\shell\open\command"; ValueType: string; ValueData: """{app}\SmartStudio.exe"" ""%1"""
Root: HKA; Subkey: "Software\Classes\SmartStudio.Badge"; ValueType: string; ValueData: "SmartStudio badge"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\SmartStudio.Badge\shell\open\command"; ValueType: string; ValueData: """{app}\SmartStudio.exe"" ""%1"""

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"

[Files]
Source: "$studioImage\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\SmartStudio"; Filename: "{app}\SmartStudio.exe"; IconFilename: "{app}\SmartStudio.exe"
Name: "{userdesktop}\SmartStudio"; Filename: "{app}\SmartStudio.exe"; IconFilename: "{app}\SmartStudio.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\SmartStudio.exe"; Description: "Open SmartStudio"; Flags: nowait skipifsilent
Filename: "{app}\SmartStudio.exe"; Flags: nowait; Check: ShouldReopenStudio

[Code]
procedure SHChangeNotify(EventId: Integer; Flags: Cardinal; Item1, Item2: Integer);
  external 'SHChangeNotify@shell32.dll stdcall';

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
    { Refresh Explorer's cached application and shortcut icons without restarting it. }
    SHChangeNotify(`$08000000, 0, 0, 0);
end;

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
    if($UpdateOnly){
        $studioSetup=$studioSetup.Replace('Source: "'+$studioImage+'\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs','Source: "'+$studioImage+'\*"; DestDir: "{app}"; Excludes: "runtime\*"; Flags: ignoreversion recursesubdirs createallsubdirs')
        $studioSetup=$studioSetup.Replace('SetupLogging=yes',"SetupLogging=yes`nUninstallable=no`nDisableDirPage=yes`nDisableReadyPage=yes")
        $studioSetup=[regex]::Replace($studioSetup,'(?s)\[Tasks\].*?(?=\[Files\])','')
        # Refresh existing shortcuts on updates, but do not create a desktop
        # shortcut for someone who chose not to have one.
        $studioSetup=$studioSetup.Replace('; Tasks: desktopicon', '; Check: FileExists(ExpandConstant(''{userdesktop}\SmartStudio.lnk''))')
        $studioSetup=$studioSetup.Replace('[Code]',@'
[Code]
function InitializeSetup: Boolean;
var
  Existing: String;
begin
  Existing := ExpandConstant('{localappdata}\Programs\SmartStudio');
  RegQueryStringValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{AAC74691-2C85-4B6C-94B1-ABDBA15DA407}_is1', 'InstallLocation', Existing);
  Existing := ExpandConstant('{param:DIR|' + Existing + '}');
  Result := FileExists(Existing + '\SmartStudio.exe') and FileExists(Existing + '\runtime\bin\javaw.exe');
  if not Result then MsgBox('This update requires an existing SmartStudio installation. Use the full installer for a new computer.', mbError, MB_OK);
end;
'@)
    }
    [IO.File]::WriteAllText($studioInstallerScript,$studioSetup,[Text.UTF8Encoding]::new($false))
    & iscc /Q $studioInstallerScript
    if ($LASTEXITCODE -ne 0) { throw 'SmartStudio installer build failed.' }
    $studioInstallerFile = Join-Path $studioTarget ($studioInstallerName+'.exe')
    Get-Item -LiteralPath $studioInstallerFile | Select-Object FullName,Length
    (Get-FileHash -LiteralPath $studioInstallerFile -Algorithm SHA256).Hash
}
