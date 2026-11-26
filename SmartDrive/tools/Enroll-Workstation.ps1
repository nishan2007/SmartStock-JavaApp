[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$PublicOrigin,
    [ValidatePattern('^[D-Z]$')][string]$DriveLetter,
    [switch]$ReplaceCredential
)
$ErrorActionPreference = 'Stop'
$origin = $null
if (-not [uri]::TryCreate($PublicOrigin,[UriKind]::Absolute,[ref]$origin) -or
    $origin.Scheme -ne 'https' -or $origin.UserInfo -or $origin.AbsolutePath -ne '/' -or $origin.Query -or $origin.Fragment) {
    throw 'Supply the DeckersDrive HTTPS origin.'
}
$secret = Read-Host 'One-time workstation setup code' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $code = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $result = Invoke-RestMethod -Method Post -Uri ($origin.GetLeftPart([UriPartial]::Authority) + '/employees/drive/api/enroll') -Headers @{Origin=$origin.GetLeftPart([UriPartial]::Authority)} -ContentType 'application/json' -Body (@{code=$code} | ConvertTo-Json) -TimeoutSec 30
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $code = $null
    $secret.Dispose()
}
try {
    $password = ConvertTo-SecureString $result.password -AsPlainText -Force
    $credential = New-Object Management.Automation.PSCredential($result.username,$password)
    $arguments = @{Server=$result.server; Share=$result.share; Credential=$credential; ReplaceCredential=$ReplaceCredential}
    if ($DriveLetter) { $arguments.DriveLetter=$DriveLetter }
    & (Join-Path $PSScriptRoot 'Connect-Workstation.ps1') @arguments
} finally {
    $result = $null
    if ($password) { $password.Dispose() }
}
