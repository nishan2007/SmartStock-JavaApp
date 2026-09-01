[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Email,
    [string]$OutputDirectory = (Join-Path $env:LOCALAPPDATA 'SmartStock\wallet-signing')
)

$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'This preparation script requires Windows DPAPI.' }
if ($Email -notmatch '^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$') {
    throw 'Supply a valid certificate contact email address.'
}
$keytool = (Get-Command keytool.exe -ErrorAction Stop).Source
$destination = [IO.Path]::GetFullPath($OutputDirectory)
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..')).TrimEnd('\')
if ($destination.Equals($repository, [StringComparison]::OrdinalIgnoreCase) -or
    $destination.StartsWith($repository + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Signing material must be outside the repository.'
}
if (Test-Path -LiteralPath $destination) { throw 'Output directory already exists. Refusing to overwrite signing material.' }
New-Item -ItemType Directory -Path $destination | Out-Null
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$acl = Get-Acl -LiteralPath $destination
$acl.SetAccessRuleProtection($true, $false)
$rule = [Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
$acl.AddAccessRule($rule)
Set-Acl -LiteralPath $destination -AclObject $acl

Add-Type -AssemblyName System.Security
$random = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($random)
$password = [Convert]::ToBase64String($random)
$passwordBytes = [Text.Encoding]::UTF8.GetBytes($password)
$previousPassword = $env:SMARTSTOCK_CSR_PASSWORD
try {
    $protected = [Security.Cryptography.ProtectedData]::Protect($passwordBytes, $null,
        [Security.Cryptography.DataProtectionScope]::CurrentUser)
    [IO.File]::WriteAllBytes((Join-Path $destination 'signing-password.dpapi'), $protected)
    $env:SMARTSTOCK_CSR_PASSWORD = $password
    $keystore = Join-Path $destination 'wallet-signing.p12'
    $request = Join-Path $destination 'wallet.certSigningRequest'
    & $keytool -genkeypair -alias wallet -keyalg RSA -keysize 2048 -sigalg SHA256withRSA `
        -dname "CN=SmartStock Wallet Signing,EMAILADDRESS=$Email" -validity 365 `
        -storetype PKCS12 -keystore $keystore -storepass:env SMARTSTOCK_CSR_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key creation failed; preserve this folder for inspection.' }
    & $keytool -certreq -alias wallet -sigalg SHA256withRSA -keystore $keystore `
        -storepass:env SMARTSTOCK_CSR_PASSWORD -file $request
    if ($LASTEXITCODE -ne 0) { throw 'Certificate request creation failed.' }
    & $keytool -printcertreq -file $request | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Certificate request validation failed.' }
    Write-Output "Certificate request prepared: $request"
    Write-Output 'Upload only wallet.certSigningRequest to Apple. Keep the P12 and DPAPI file private.'
    Write-Output 'The temporary self-signed certificate cannot sign a pass accepted by Apple Wallet.'
} finally {
    $env:SMARTSTOCK_CSR_PASSWORD = $previousPassword
    [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
    [Array]::Clear($random, 0, $random.Length)
    $password = $null
    $rng.Dispose()
}
