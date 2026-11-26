param([switch]$FullSuite)
$ErrorActionPreference='Stop'
$draftPg='C:/Program Files/PostgreSQL/17/bin'
$draftRoot=Join-Path ([IO.Path]::GetTempPath()) ('smartstock-ad-drafts-'+[guid]::NewGuid())
New-Item -ItemType Directory -Path $draftRoot | Out-Null
$draftData=Join-Path $draftRoot 'data'
& "$draftPg/initdb.exe" -D $draftData -U postgres -A trust --no-locale *> (Join-Path $draftRoot 'init.log')
if($LASTEXITCODE -ne 0){throw 'Disposable PostgreSQL initialization failed'}
$draftStarted=$false
Push-Location (Join-Path $PSScriptRoot '../..')
try {
 & "$draftPg/pg_ctl.exe" -D $draftData -l (Join-Path $draftRoot 'server.log') -o '-p 56560 -h 127.0.0.1' -w start
 if($LASTEXITCODE -ne 0){throw 'Disposable PostgreSQL startup failed'}
 $draftStarted=$true
 & "$draftPg/createdb.exe" -h 127.0.0.1 -p 56560 -U postgres ad_draft_test
 if($LASTEXITCODE -ne 0){throw 'Test database creation failed'}
 $draftArgs=@('-q','-f','SmartStock/pom.xml','-Dsmartstock.test.adDraftDatabaseUrl=jdbc:postgresql://127.0.0.1:56560/ad_draft_test')
 if(-not $FullSuite){$draftArgs+='-Dtest=ProductAdDraftStoreTest,ProductAdDraftDatabaseTest,AdDraftRendererTest,ProductImageCoverageDialogTest'}
 & mvn @draftArgs test
 if($LASTEXITCODE -ne 0){throw "Ad draft checks failed; logs: $draftRoot"}
} finally {
 if($draftStarted){& "$draftPg/pg_ctl.exe" -D $draftData -m fast -w stop}
 Pop-Location
}
