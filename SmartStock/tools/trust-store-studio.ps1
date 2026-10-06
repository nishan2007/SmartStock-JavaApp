param(
    [Parameter(Mandatory=$true)][string]$Certificate,
    [Parameter(Mandatory=$true)][string]$ExpectedSha256,
    [string]$WebHost='studio.deckers.gy',
    [switch]$VerifyOnly
)
$ErrorActionPreference='Stop'
$path=(Resolve-Path -LiteralPath $Certificate).Path
if ([IO.Path]::GetExtension($path) -ine '.cer') {throw 'Select the public .cer certificate; never copy a .p12 private key.'}
if ($ExpectedSha256 -notmatch '^[a-fA-F0-9]{64}$' -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $ExpectedSha256) {throw 'The certificate fingerprint does not match the store server.'}
$cert=[Security.Cryptography.X509Certificates.X509Certificate2]::new($path)
if ($cert.HasPrivateKey -or $cert.NotBefore -gt (Get-Date) -or $cert.NotAfter -lt (Get-Date)) {throw 'The public certificate must be valid and contain no private key.'}
$san=@($cert.Extensions | Where-Object {$_.Oid.Value -eq '2.5.29.17'})
$hostPattern='(?i)(?<![a-z0-9.-])'+[regex]::Escape($WebHost)+'(?![a-z0-9.-])'
if ($san.Count -ne 1 -or $san[0].Format($true) -notmatch $hostPattern) {throw 'The certificate belongs to a different web address.'}
if ($VerifyOnly) {Write-Output 'Public studio certificate verified.';return}
$store=[Security.Cryptography.X509Certificates.X509Store]::new('Root','CurrentUser')
try {$store.Open('ReadWrite');$store.Add($cert)} finally {$store.Close()}
Write-Host ('Studio certificate trusted for this Windows account. Open https://'+$WebHost+':8444/background-remover.html and activate/sign in.')
