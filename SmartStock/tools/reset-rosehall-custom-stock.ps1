param(
    [switch]$Apply,
    [string]$ProfileDirectory=(Join-Path $env:USERPROFILE '.smartstock\profiles\production'),
    [string]$Psql='C:\Program Files\PostgreSQL\17\bin\psql.exe'
)
$ErrorActionPreference='Stop'
$properties=@{}
Get-Content -LiteralPath (Join-Path $ProfileDirectory 'database.properties') | ForEach-Object {
    if($_ -match '^([^#!][^=]*)=(.*)$'){$properties[$matches[1].Trim()]=$matches[2].Trim().Replace('\:',':').Replace('\=','=')}
}
if($properties['mode'] -ne 'SERVER' -or $properties['location.id'] -ne '2') {
    throw 'Run this on the Rosehall production server (SERVER, location 2). No changes made.'
}
if(!(Test-Path -LiteralPath $Psql)){throw 'PostgreSQL psql was not found.'}
if(($properties.ContainsKey('server.port') -and $properties['server.port'] -ne '5432') -or
   ($properties.ContainsKey('database.name') -and $properties['database.name'] -ne 'smartstock') -or
   ($properties.ContainsKey('jdbc.url') -and $properties['jdbc.url'] -notmatch '^jdbc:postgresql://(127\.0\.0\.1|localhost):5432/smartstock$')) {
    throw 'Nonstandard database connection. Review the server connection before using this repair.'
}
if(!$Apply){Write-Output 'Rosehall server configuration verified. Use -Apply to back up balances and reset only Rosehall custom quantities to zero.';return}
$evidenceDirectory=Join-Path $env:USERPROFILE '.smartstock\maintenance\rosehall-custom-stock-reset-20261124'
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null
$owner=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $evidenceDirectory /inheritance:r /grant:r ('*'+$owner+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' | Out-Null
if($LASTEXITCODE -ne 0){throw 'Could not protect the stock evidence directory.'}
if(Test-Path -LiteralPath (Join-Path $evidenceDirectory 'completed.txt')){throw 'Reset already completed. Refusing to erase subsequent stock.'}
$evidenceFile=Join-Path $evidenceDirectory ('before-'+[guid]::NewGuid().ToString('N')+'.csv')
if($evidenceFile -match "['\r\n]"){throw 'Unsupported evidence path.'}
$sql=(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'reset-rosehall-custom-stock.sql') -Raw).Replace('__EVIDENCE_FILE__',$evidenceFile.Replace('\','/'))
function Read-ServerSecret([string]$key) {
    $secretFile=Join-Path $env:USERPROFILE ('.smartstock\credentials\smartstock-production-'+$key+'.dpapi')
    $secret=Get-Content -Raw -LiteralPath $secretFile | ConvertTo-SecureString
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try {[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)} finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)}
}
$savedPgUser=$env:PGUSER;$savedPgPassword=$env:PGPASSWORD;$savedPgOptions=$env:PGOPTIONS
try {
    $env:PGUSER=Read-ServerSecret 'primary-db-user'
    $env:PGPASSWORD=Read-ServerSecret 'primary-db-password'
    $env:PGOPTIONS='-c timezone=America/Guyana'
    # Use the local store authority, never the off-site mirror or a register database.
    $result=$sql | & $Psql -X -h 127.0.0.1 -p 5432 -d smartstock -v ON_ERROR_STOP=1 -P pager=off 2>&1
    $result | Tee-Object -FilePath (Join-Path $evidenceDirectory 'result.txt')
    if($LASTEXITCODE -ne 0){throw 'Reset failed. Check result.txt; failed transactions roll back automatically.'}
    [IO.File]::WriteAllText((Join-Path $evidenceDirectory 'completed.txt'),[DateTimeOffset]::Now.ToString('o'))
    Write-Output ('Verified Rosehall custom quantities are zero. Evidence: '+$evidenceDirectory)
} finally {
    $env:PGUSER=$savedPgUser;$env:PGPASSWORD=$savedPgPassword;$env:PGOPTIONS=$savedPgOptions
}
