[CmdletBinding()]
param([string]$PostgresBin = 'C:\Program Files\PostgreSQL\17\bin')
$ErrorActionPreference = 'Stop'
$port = 55467
foreach ($name in @('initdb.exe','pg_ctl.exe','createdb.exe','psql.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin $name) -PathType Leaf)) { throw "Missing PostgreSQL tool: $name" }
}
if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
    throw 'The disposable submission-test port is already occupied. No database was changed.'
}
$temporaryParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
$workspace = Join-Path $temporaryParent ('smartdrive-submission-db-'+[guid]::NewGuid())
$data = Join-Path $workspace 'data'
$log = Join-Path $workspace 'postgres.log'
$pom = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\SmartStock\pom.xml'))
$passed = $false
$stopped = $true
try {
    [void][IO.Directory]::CreateDirectory($workspace)
    & (Join-Path $PostgresBin 'initdb.exe') -D $data -U postgres --auth=trust --encoding=UTF8 --no-locale
    if ($LASTEXITCODE) { throw 'Disposable PostgreSQL initialization failed.' }
    # This short-lived synthetic cluster accepts only its two local test databases.
    @('host postgres postgres 127.0.0.1/32 trust',
      'host template_submission_test postgres 127.0.0.1/32 trust') |
        Set-Content -LiteralPath (Join-Path $data 'pg_hba.conf') -Encoding ASCII
    $stopped = $false
    & (Join-Path $PostgresBin 'pg_ctl.exe') -D $data -l $log -w -t 30 -o '-h 127.0.0.1 -p 55467 -c shared_buffers=32MB -c max_connections=20' start
    if ($LASTEXITCODE) { throw 'Disposable PostgreSQL startup failed.' }
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $port -U postgres template_submission_test
    if ($LASTEXITCODE) { throw 'Disposable test database creation failed.' }
    $actualData = & (Join-Path $PostgresBin 'psql.exe') -X -A -t -h 127.0.0.1 -p $port -U postgres -d template_submission_test -c 'SHOW data_directory'
    if ($LASTEXITCODE -or [IO.Path]::GetFullPath(([string]$actualData).Trim()) -ne [IO.Path]::GetFullPath($data)) {
        throw 'Test database did not report the isolated cluster directory.'
    }
    $testStarted = [DateTime]::UtcNow
    & mvn -q -f $pom '-Dtest=StudioSubmissionQueueDatabaseTest' '-Dsmartstock.test.submissionDatabaseUrl=jdbc:postgresql://127.0.0.1:55467/template_submission_test' test
    if ($LASTEXITCODE) { throw 'Submission database integration tests failed.' }
    $reportPath = Join-Path (Split-Path -Path $pom -Parent) 'target\surefire-reports\TEST-services.StudioSubmissionQueueDatabaseTest.xml'
    if ((Get-Item -LiteralPath $reportPath).LastWriteTimeUtc -lt $testStarted) { throw 'The database test report was not refreshed.' }
    [xml]$report = Get-Content -LiteralPath $reportPath -Raw
    if ([int]$report.testsuite.tests -lt 9 -or [int]$report.testsuite.skipped -ne 0 -or
        [int]$report.testsuite.failures -ne 0 -or [int]$report.testsuite.errors -ne 0) {
        throw 'Database integration tests must execute successfully with no skipped cases.'
    }
    Write-Output "$($report.testsuite.tests) submission database integration tests passed with none skipped."
    $passed = $true
} finally {
    if (-not $stopped) {
        & (Join-Path $PostgresBin 'pg_ctl.exe') -D $data status | Out-Null
        if ($LASTEXITCODE -eq 0) {
            & (Join-Path $PostgresBin 'pg_ctl.exe') -D $data -w -t 30 -m fast stop
            $stopped = $LASTEXITCODE -eq 0
        } elseif ($LASTEXITCODE -eq 3) { $stopped = $true }
    }
    if ($passed -and $stopped) {
        $resolved = [IO.Path]::GetFullPath($workspace)
        if ([IO.Path]::GetDirectoryName($resolved) -ne $temporaryParent -or
            [IO.Path]::GetFileName($resolved) -notmatch '^smartdrive-submission-db-[a-f0-9-]{36}$' -or
            (Get-Item -LiteralPath $resolved -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'Refusing cleanup outside the unique temporary test directory.'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
        Write-Output 'Submission database tests passed; isolated cluster stopped and removed.'
    } else { Write-Output "Test evidence retained at $workspace. Cluster stopped: $stopped" }
}
