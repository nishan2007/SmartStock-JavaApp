param([string]$OutputDirectory = (Join-Path $PSScriptRoot '..\target\release-windows-1.0.223'))
$ErrorActionPreference = 'Stop'
throw 'This historical patch packager cannot preserve independent AI models. Use package-windows-release.ps1 -UpdateOnly to package the current complete application.'
$Version = '1.0.223'
$BaseApp = 'C:\Program Files\SmartStock\app'
$BaseJar = Join-Path $BaseApp 'inventory-management-1.0.222.jar'
$ExpectedBaseHash = '69437ab55d9d729cc4c3db06f13e75b6cd376e424817ce3e578dd96e16db67d0'
if (-not (Test-Path -LiteralPath $BaseJar) -or
    (Get-FileHash -LiteralPath $BaseJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $ExpectedBaseHash) {
    throw 'The verified installed SmartStock 1.0.222 application is required as the base.'
}
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Output = [IO.Path]::GetFullPath($OutputDirectory)
$Work = Join-Path $env:TEMP ('smartstock-photo-review-' + [guid]::NewGuid())
try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.IO.Compression
    $Payload = Join-Path $Work 'payload'
    $Classes = Join-Path $Work 'classes'
    $Dependency = Join-Path $Payload 'dependency'
    New-Item -ItemType Directory -Force -Path $Classes,$Dependency | Out-Null
    Copy-Item -Path (Join-Path $BaseApp 'dependency\*') -Destination $Dependency -Recurse
    $Jar = Join-Path $Payload "inventory-management-$Version.jar"
    Copy-Item -LiteralPath $BaseJar -Destination $Jar
    $Classpath = $BaseJar + ';' + (Join-Path $BaseApp 'dependency\*')
    & javac -cp $Classpath -d $Classes `
        (Join-Path $Root 'src\services\CatalogPhotoGalleryService.java') `
        (Join-Path $Root 'src\services\LanApiServer.java') `
        (Join-Path $Root 'src\services\LanApiClient.java') `
        (Join-Path $Root 'src\ui\screens\CatalogPhotoStudioDialog.java')
    if ($LASTEXITCODE -ne 0) { throw 'The photo-review changes did not compile against SmartStock 1.0.222.' }
    & jar uf $Jar -C $Classes .
    if ($LASTEXITCODE -ne 0) { throw 'The photo-review classes could not be added to the application JAR.' }
    $Manifest = [IO.Compression.ZipFile]::Open($Jar,[IO.Compression.ZipArchiveMode]::Update)
    try {
        $Old = $Manifest.GetEntry('META-INF/MANIFEST.MF')
        if ($null -eq $Old) { throw 'The application manifest is missing.' }
        $Reader = [IO.StreamReader]::new($Old.Open(),[Text.Encoding]::ASCII)
        try { $Content = $Reader.ReadToEnd() } finally { $Reader.Dispose() }
        if (-not $Content.Contains('Implementation-Version: 1.0.222')) { throw 'Unexpected base version.' }
        $Old.Delete()
        $Entry = $Manifest.CreateEntry('META-INF/MANIFEST.MF')
        $Writer = [IO.StreamWriter]::new($Entry.Open(),[Text.Encoding]::ASCII)
        try { $Writer.Write($Content.Replace('Implementation-Version: 1.0.222',"Implementation-Version: $Version")) }
        finally { $Writer.Dispose() }
    } finally { $Manifest.Dispose() }
    $Meta = Join-Path $Work 'META-INF\maven\edu.fiu\inventory-management'
    New-Item -ItemType Directory -Force -Path $Meta | Out-Null
    Set-Content -LiteralPath (Join-Path $Meta 'pom.properties') -Encoding ASCII -Value @(
        'groupId=edu.fiu', 'artifactId=inventory-management', "version=$Version")
    & jar uf $Jar -C $Work META-INF/maven/edu.fiu/inventory-management/pom.properties
    if ($LASTEXITCODE -ne 0) { throw 'Could not update the application version metadata.' }
    New-Item -ItemType Directory -Force -Path $Output | Out-Null
    $Zip = Join-Path $Output "smartstock-windows-$Version.zip"
    if (Test-Path -LiteralPath $Zip) { throw "Refusing to overwrite $Zip" }
    $Archive = [IO.Compression.ZipFile]::Open($Zip,[IO.Compression.ZipArchiveMode]::Create)
    try {
        $Prefix = $Payload.TrimEnd('\') + '\'
        Get-ChildItem -LiteralPath $Payload -File -Recurse | ForEach-Object {
            $Name = $_.FullName.Substring($Prefix.Length).Replace('\','/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($Archive,$_.FullName,$Name,
                [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally { $Archive.Dispose() }
    $Check = [IO.Compression.ZipFile]::OpenRead($Zip)
    try {
        $Names = @($Check.Entries | ForEach-Object FullName)
        if ($Names -notcontains "inventory-management-$Version.jar" -or
            $Names -notcontains 'dependency/catalog-studio/isnet-general-use.onnx' -or
            -not @($Names | Where-Object { $_ -like 'dependency/onnxruntime-*.jar' }).Count -or
            @($Names | Where-Object { $_.Contains('\') }).Count) { throw 'The updater payload is incomplete.' }
    } finally { $Check.Dispose() }
    Write-Host "Release artifact: $Zip"
    Write-Host "Size: $((Get-Item -LiteralPath $Zip).Length)"
    Write-Host "SHA-256: $((Get-FileHash -LiteralPath $Zip -Algorithm SHA256).Hash.ToLowerInvariant())"
} finally {
    $Resolved = [IO.Path]::GetFullPath($Work)
    $Temp = [IO.Path]::GetFullPath($env:TEMP).TrimEnd('\') + '\'
    if (-not $Resolved.StartsWith($Temp,[StringComparison]::OrdinalIgnoreCase) -or
        -not ([IO.Path]::GetFileName($Resolved) -match '^smartstock-photo-review-[a-f0-9-]{36}$')) {
        throw 'Refusing cleanup outside the verified temporary directory.'
    }
    if (Test-Path -LiteralPath $Resolved) { Remove-Item -LiteralPath $Resolved -Recurse -Force }
}
