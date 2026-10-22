$ErrorActionPreference = 'Stop'
$tokens = $null; $parseErrors = $null
$source = Join-Path $PSScriptRoot '..\src\windows\network-maintenance.ps1'
$ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
# Exercise the production firewall repair loop without touching Windows state.
$loop = $ast.Find({ param($node) $node -is [Management.Automation.Language.ForEachStatementAst] -and $node.Variable.VariablePath.UserPath -eq 'spec' -and $node.Extent.Text.Contains("'SmartStockManaged-'") }, $true)
if (-not $loop) { throw 'Firewall maintenance loop not found.' }
$scope = '192.168.50.0/24'
function Get-NetFirewallRule {
    param($Name)
    if ($script:scenario -eq 'missing') { return }
    [pscustomobject]@{ Name=$Name; Enabled=$(if ($script:scenario -eq 'disabled') { 'False' } else { 'True' }); Direction='Inbound'; Action='Allow'; Profile='Private' }
}
function Get-NetFirewallPortFilter {
    process {
        $number = switch ($_.Name) { 'SmartStockManaged-API' {8443}; 'SmartStockManaged-Discovery' {18443}; 'SmartStockManaged-MobileUI' {8444}; 'SmartStockManaged-MobileAPI' {8445}; 'SmartStockManaged-EmployeeRegistration' {8448} }
        [pscustomobject]@{ Protocol=$(if ($number -eq 18443) { 'UDP' } else { 'TCP' }); LocalPort=$number; RemotePort='Any' }
    }
}
function Get-NetFirewallAddressFilter { process { [pscustomobject]@{LocalAddress='Any';RemoteAddress=$(if ($script:scenario -eq 'scope-reset') {'Any'} else {$scope})} } }
function Get-NetFirewallApplicationFilter { process { [pscustomobject]@{Program=$(if ($script:scenario -eq 'program-reset') {'wrong.exe'} else {'Any'})} } }
function Remove-NetFirewallRule { param($Name) $script:removed++ }
function New-NetFirewallRule {
    param($Name,$DisplayName,$Direction,$Enabled,$Action,$Profile,$Protocol,$LocalPort,$RemoteAddress)
    if ($Profile -ne 'Private' -or $RemoteAddress -ne $scope -or $Action -ne 'Allow' -or $Direction -ne 'Inbound') { throw 'Repair widened LAN access.' }
    $script:created++
}
function Write-RepairLog { param($message) }
foreach ($case in @('healthy','missing','disabled','scope-reset','program-reset')) {
    $script:scenario=$case; $script:created=0; $script:removed=0
    & ([scriptblock]::Create($loop.Extent.Text))
    $expected = if ($case -eq 'healthy') { 0 } else { 5 }
    if ($script:created -ne $expected) { throw "$case expected $expected repairs, got $script:created" }
    if ($case -in @('healthy','missing') -and $script:removed -ne 0) { throw 'Unexpected rule removal.' }
    Write-Output "$case passed"
}
