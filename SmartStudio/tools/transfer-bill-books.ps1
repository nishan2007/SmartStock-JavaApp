param(
    [Parameter(Mandatory=$true)][string]$SourceRoot,
    [Parameter(Mandatory=$true)][string]$DestinationRoot,
    [Parameter(Mandatory=$true)][string]$EvidenceRoot,
    [string]$WaitForProcessIds = ''
)
$ErrorActionPreference='Stop'
$studioRoot=Split-Path -Parent $PSScriptRoot
foreach($transferProcessId in ($WaitForProcessIds -split ',' | Where-Object {$_ -ne ''})){
    $transferProcess=Get-Process -Id ([int]$transferProcessId) -ErrorAction SilentlyContinue
    if($transferProcess){$transferProcess.WaitForExit()}
}
Push-Location $studioRoot
try{
    & mvn -q compile dependency:build-classpath '-Dmdep.outputFile=target/billbook-classpath.txt'
    if($LASTEXITCODE -ne 0){throw 'Bill book tools did not compile.'}
    $transferClasspath=(Join-Path $studioRoot 'target/classes')+';'+(Get-Content 'target/billbook-classpath.txt' -Raw).Trim()
    $registerHome=[Environment]::GetFolderPath('UserProfile')
    $transferFailures=$false
    foreach($sourceName in @('Bill Books','ORIGINAL BILLBOOK')){
        $sourceFolder=Join-Path $SourceRoot $sourceName
        if(-not(Test-Path -LiteralPath $sourceFolder -PathType Container)){throw "Missing source folder: $sourceName"}
        & java -Xmx2g "-Dsmartstudio.register.home=$registerHome" '-Djava.awt.headless=true' -cp $transferClasspath gy.deckers.studio.BillBookTransfer $sourceFolder (Join-Path $DestinationRoot $sourceName) (Join-Path $EvidenceRoot $sourceName)
        if($LASTEXITCODE -ne 0){$transferFailures=$true}
    }
    & java -Xmx2g '-Djava.awt.headless=true' -cp $transferClasspath gy.deckers.studio.BillBookTransferAudit $EvidenceRoot
    if($LASTEXITCODE -ne 0){throw 'Archive audit failed. Check transfer reports.'}
    if($transferFailures){throw 'Some templates need attention. Read transfer.tsv and archive-review.tsv; original files remain unchanged.'}
}finally{Pop-Location}
