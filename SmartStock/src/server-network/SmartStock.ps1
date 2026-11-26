function Get-CurrentPluginType { 'dns-01' }
function Invoke-SmartStockNetwork {
    param([string]$Operation,[hashtable]$Fields)
    $config = Get-Content -Raw -LiteralPath (Join-Path $env:USERPROFILE '.smartstock\network-provisioning\bootstrap.json') | ConvertFrom-Json
    $classpath = $config.jar + ';' + (Join-Path (Split-Path $config.jar) 'dependency\*')
    if ($config.helperJar) {$classpath=$config.helperJar+';'+$classpath}
    $result = ($Fields | ConvertTo-Json -Compress) | & $config.java ('-Dsmartstock.environment=' + $config.environment) -cp $classpath services.StoreNetworkProvisioning $Operation
    if ($LASTEXITCODE -ne 0) { throw 'Authenticated store network provisioning failed.' }
    return ($result | ConvertFrom-Json)
}
function Add-DnsTxt {
    [CmdletBinding()]param([Parameter(Mandatory,Position=0)][string]$RecordName,
        [Parameter(Mandatory,Position=1)][string]$TxtValue,[Parameter(ValueFromRemainingArguments)]$ExtraParams)
    Invoke-SmartStockNetwork '/v1/dns/present' @{recordName=$RecordName;value=$TxtValue} | Out-Null
}
function Remove-DnsTxt {
    [CmdletBinding()]param([Parameter(Mandatory,Position=0)][string]$RecordName,
        [Parameter(Mandatory,Position=1)][string]$TxtValue,[Parameter(ValueFromRemainingArguments)]$ExtraParams)
    Invoke-SmartStockNetwork '/v1/dns/cleanup' @{recordName=$RecordName;value=$TxtValue} | Out-Null
}
function Save-DnsTxt {
    [CmdletBinding()]param([Parameter(ValueFromRemainingArguments)]$ExtraParams)
}
