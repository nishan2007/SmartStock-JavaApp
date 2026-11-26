param([switch]$FullSuite)
$ErrorActionPreference='Stop'
$mergePg='C:/Program Files/PostgreSQL/17/bin'
$mergeRoot=Join-Path ([IO.Path]::GetTempPath()) ('smartstock-product-merges-'+[guid]::NewGuid())
New-Item -ItemType Directory -Path $mergeRoot | Out-Null
$mergeData=Join-Path $mergeRoot 'data'
& "$mergePg/initdb.exe" -D $mergeData -U postgres -A trust --no-locale *> (Join-Path $mergeRoot 'init.log')
if($LASTEXITCODE -ne 0){throw 'Disposable PostgreSQL initialization failed'}
$mergeStarted=$false
Push-Location (Join-Path $PSScriptRoot '../..')
try {
 & "$mergePg/pg_ctl.exe" -D $mergeData -l (Join-Path $mergeRoot 'server.log') -o '-p 56559 -h 127.0.0.1' -w start
 if($LASTEXITCODE -ne 0){throw 'Disposable PostgreSQL startup failed'}
 $mergeStarted=$true
 & "$mergePg/createdb.exe" -h 127.0.0.1 -p 56559 -U postgres product_merge_test
 if($LASTEXITCODE -ne 0){throw 'Test database creation failed'}
 $mergeArgs=@('-q','-f','SmartStock/pom.xml','-Dsmartstock.test.productMergeDatabaseUrl=jdbc:postgresql://127.0.0.1:56559/product_merge_test')
 if(-not $FullSuite){$mergeArgs+='-Dtest=ProductMergeServiceTest,ProductMergeDatabaseTest'}
 & mvn @mergeArgs test
 if($LASTEXITCODE -ne 0){throw "Product merge checks failed; logs: $mergeRoot"}
} finally {
 if($mergeStarted){& "$mergePg/pg_ctl.exe" -D $mergeData -m fast -w stop}
 Pop-Location
}
