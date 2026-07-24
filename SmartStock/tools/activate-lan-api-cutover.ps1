param([switch]$Confirm)
$ErrorActionPreference = 'Stop'
if (-not $Confirm) { throw 'Run on the physical server with -Confirm after all registers are upgraded and paired.' }

$AppDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$violations = Get-ChildItem (Join-Path $AppDir 'src\ui'), (Join-Path $AppDir 'src\managers'), (Join-Path $AppDir 'src\Receipt') -Recurse -Filter *.java |
    Select-String -Pattern 'DB\.getConnection\(|DriverManager\.getConnection\(' | Select-Object -ExpandProperty Path -Unique
if ($violations) { throw "LAN API cutover blocked; register-callable JDBC remains:`n$($violations -join "`n")" }

function Read-DpapiCredential([string]$Name) {
    $path = Join-Path $env:USERPROFILE ".smartstock\credentials\$Name.dpapi"
    $secure = Get-Content -Raw -LiteralPath $path | ConvertTo-SecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

$configPath = Join-Path $env:USERPROFILE '.smartstock\database.properties'
$dbUser = Read-DpapiCredential 'primary-db-user'
$env:PGPASSWORD = Read-DpapiCredential 'primary-db-password'
$dbPortLine = Get-Content $configPath | Where-Object { $_ -match '^server\.port=' } | Select-Object -Last 1
$dbPort = if ($dbPortLine) { [int]($dbPortLine -replace '^server\.port=', '') } else { 5432 }
# Pre-launch SmartStock has no mixed-client period. Database isolation is never
# delayed by an unpaired development device.

$sql = @"
DO `$`$
DECLARE role_name text;
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartstock_client') THEN ALTER ROLE smartstock_client NOLOGIN; END IF;
  FOR role_name IN SELECT rolname FROM pg_roles WHERE rolname LIKE 'smartstock_device_%' LOOP
    EXECUTE format('ALTER ROLE %I NOLOGIN', role_name);
  END LOOP;
END `$`$;
INSERT INTO security_audit_events(event_type, details) VALUES
('LAN_API_CUTOVER_ACTIVATED', 'Direct register JDBC roles disabled after zero-JDBC architecture check');
ALTER SYSTEM SET listen_addresses = 'localhost';
"@
$sql | psql -h 127.0.0.1 -p $dbPort -U $dbUser -d smartstock -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL cutover failed.' }

Get-Service 'postgresql*' -ErrorAction SilentlyContinue | Restart-Service -Force
$credentialsNote = Join-Path $env:USERPROFILE '.smartstock\database-credentials.txt'
if (Test-Path $credentialsNote) {
    Copy-Item $credentialsNote ($credentialsNote + '.pre-lan-api-lockdown') -Force
    (Get-Content $credentialsNote) |
        Where-Object { $_ -notmatch '^SMARTSTOCK_(CLIENT_DB_[A-Z_]+|DB_PASSWORD)=' -and $_ -ne '# Use these on register/client computers.' } |
        Set-Content $credentialsNote -Encoding UTF8
}
Get-ChildItem (Join-Path $env:USERPROFILE '.smartstock\database-credentials.txt*') -File -ErrorAction SilentlyContinue | ForEach-Object {
    (Get-Content $_.FullName) |
        Where-Object { $_ -notmatch '^SMARTSTOCK_(CLIENT_DB_[A-Z_]+|DB_PASSWORD)=' -and $_ -ne '# Use these on register/client computers.' } |
        Set-Content $_.FullName -Encoding UTF8
}
Get-ChildItem (Join-Path $env:USERPROFILE '.smartstock\database.properties.bak.*') -File -ErrorAction SilentlyContinue | ForEach-Object {
    (Get-Content $_.FullName) |
        ForEach-Object {
            $_ -replace '^db\.user=.*$', 'db.user=${SMARTSTOCK_SECURE_DB_USER}' `
               -replace '^db\.password=.*$', 'db.password=${SMARTSTOCK_SECURE_DB_PASSWORD}' `
               -replace '(?m)^cloud\.(?:jdbc\.url|db\.(?:user|password))=.*\r?\n?', ''
        } | Set-Content $_.FullName -Encoding UTF8
}
Write-Host 'SmartStock LAN API lockdown verified. Direct register database roles are disabled.'
