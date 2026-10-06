param(
    [string]$BaseZip = (Join-Path $PSScriptRoot '..\target\release-windows-1.0.220\smartstock-windows-1.0.220.zip'),
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\target\release-windows-1.0.221')
)
$ErrorActionPreference = 'Stop'
throw 'This historical patch packager cannot preserve independent AI models. Use package-windows-release.ps1 -UpdateOnly to package the current complete application.'
$ExpectedBaseHash = 'f00602293e03e84112432ad8365aaa361c9712eff3091f8b31cad8d17fe5a1f9'
$Version = '1.0.221'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Base = (Resolve-Path -LiteralPath $BaseZip).Path
if ((Get-FileHash -LiteralPath $Base -Algorithm SHA256).Hash.ToLowerInvariant() -ne $ExpectedBaseHash) {
    throw 'The base updater is not the verified, published SmartStock 1.0.220 artifact.'
}
$Output = [IO.Path]::GetFullPath($OutputDirectory)
$Work = Join-Path $env:TEMP ('smartstock-catalog-studio-' + [guid]::NewGuid())
try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.IO.Compression
    $Payload = Join-Path $Work 'payload'
    [IO.Compression.ZipFile]::ExtractToDirectory($Base, $Payload)
    $OldJar = Join-Path $Payload 'inventory-management-1.0.220.jar'
    $NewJar = Join-Path $Payload "inventory-management-$Version.jar"
    if (-not (Test-Path -LiteralPath $OldJar)) { throw 'The base application JAR is missing.' }
    Copy-Item -LiteralPath $OldJar -Destination $NewJar
    $Classes = Join-Path $Work 'classes'
    New-Item -ItemType Directory -Force -Path $Classes | Out-Null
    $ClassPath = $OldJar + ';' + (Join-Path $Payload 'dependency\*')
    & javac -cp $ClassPath -d $Classes (Join-Path $Root 'src\services\MobileItemWebServer.java')
    if ($LASTEXITCODE -ne 0) { throw 'The studio service did not compile against the published 1.0.220 application.' }
    & jar uf $NewJar -C $Classes services/MobileItemWebServer.class
    if ($LASTEXITCODE -ne 0) { throw 'Could not add the studio service to the application JAR.' }
    $Meta = Join-Path $Work 'META-INF\maven\edu.fiu\inventory-management'
    New-Item -ItemType Directory -Force -Path $Meta | Out-Null
    Set-Content -LiteralPath (Join-Path $Meta 'pom.properties') -Encoding ASCII -Value @(
        'groupId=edu.fiu', 'artifactId=inventory-management', "version=$Version")
    & jar uf $NewJar -C $Work META-INF/maven/edu.fiu/inventory-management/pom.properties
    if ($LASTEXITCODE -ne 0) { throw 'Could not update the Maven version metadata.' }
    $BaseJarArchive = [IO.Compression.ZipFile]::OpenRead($OldJar)
    try {
        $Entry = $BaseJarArchive.GetEntry('META-INF/MANIFEST.MF')
        if ($null -eq $Entry) { throw 'The base application manifest is missing.' }
        $Reader = [IO.StreamReader]::new($Entry.Open(), [Text.Encoding]::ASCII)
        try { $ManifestText = $Reader.ReadToEnd() } finally { $Reader.Dispose() }
    } finally { $BaseJarArchive.Dispose() }
    if (-not $ManifestText.Contains('Implementation-Version: 1.0.220')) { throw 'Unexpected base application version.' }
    $UpdatedManifest = $ManifestText.Replace('Implementation-Version: 1.0.220', "Implementation-Version: $Version")
    $NewJarArchive = [IO.Compression.ZipFile]::Open($NewJar, [IO.Compression.ZipArchiveMode]::Update)
    try {
        $OldEntry = $NewJarArchive.GetEntry('META-INF/MANIFEST.MF')
        if ($null -eq $OldEntry) { throw 'The application manifest was lost during packaging.' }
        $OldEntry.Delete()
        $NewEntry = $NewJarArchive.CreateEntry('META-INF/MANIFEST.MF')
        $Writer = [IO.StreamWriter]::new($NewEntry.Open(), [Text.Encoding]::ASCII)
        try { $Writer.Write($UpdatedManifest) } finally { $Writer.Dispose() }
    } finally { $NewJarArchive.Dispose() }
    $Tools = Join-Path $Payload 'dependency\catalog-studio'
    New-Item -ItemType Directory -Force -Path $Tools | Out-Null
    Copy-Item -LiteralPath (Join-Path $Root 'tools\catalog-studio.py') -Destination $Tools
    Copy-Item -LiteralPath (Join-Path $Root 'tools\catalog-studio.md') -Destination $Tools
    Remove-Item -LiteralPath $OldJar -Force
    New-Item -ItemType Directory -Force -Path $Output | Out-Null
    $Zip = Join-Path $Output "smartstock-windows-$Version.zip"
    if (Test-Path -LiteralPath $Zip) { throw "Refusing to overwrite $Zip" }
    $Archive = [IO.Compression.ZipFile]::Open($Zip, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $Prefix = $Payload.TrimEnd('\') + '\'
        Get-ChildItem -LiteralPath $Payload -File -Recurse | ForEach-Object {
            $Entry = $_.FullName.Substring($Prefix.Length).Replace('\', '/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($Archive, $_.FullName, $Entry,
                [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally { $Archive.Dispose() }
    $Check = [IO.Compression.ZipFile]::OpenRead($Zip)
    try {
        $Names = @($Check.Entries | ForEach-Object FullName)
        if ($Names -notcontains "inventory-management-$Version.jar" -or
            $Names -notcontains 'dependency/catalog-studio/catalog-studio.py' -or
            $Names -notcontains 'dependency/catalog-studio/catalog-studio.md' -or
            -not ($Names | Where-Object { $_ -like 'dependency/postgresql-*.jar' })) {
            throw 'The updater payload is incomplete.'
        }
        if ($Names | Where-Object { $_.Contains('\') }) { throw 'The updater ZIP contains nonportable entry names.' }
    } finally { $Check.Dispose() }
    Write-Host "Release artifact: $Zip"
    Write-Host "Version: $Version"
    Write-Host "Size: $((Get-Item -LiteralPath $Zip).Length)"
    Write-Host "SHA-256: $((Get-FileHash -LiteralPath $Zip -Algorithm SHA256).Hash.ToLowerInvariant())"
} finally {
    $Resolved = [IO.Path]::GetFullPath($Work)
    $Temp = [IO.Path]::GetFullPath($env:TEMP).TrimEnd('\') + '\'
    if (-not $Resolved.StartsWith($Temp, [StringComparison]::OrdinalIgnoreCase) -or
        -not ([IO.Path]::GetFileName($Resolved) -match '^smartstock-catalog-studio-[a-f0-9-]{36}$')) {
        throw 'Refusing cleanup outside the verified temporary directory.'
    }
    if (Test-Path -LiteralPath $Resolved) { Remove-Item -LiteralPath $Resolved -Recurse -Force }
}
