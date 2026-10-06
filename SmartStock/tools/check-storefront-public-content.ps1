param(
    [string]$Origin = 'https://www.deckers.gy',
    [string]$OutputPath,
    [switch]$CheckLocalRuntime
)

$ErrorActionPreference = 'Stop'
$base = [uri]$Origin
if ($base.Scheme -ne 'https' -or $base.AbsolutePath -ne '/' -or $base.Query -or $base.Fragment -or $base.UserInfo) {
    throw 'Origin must be an HTTPS site origin without a path, query, or credentials.'
}
$origin = $base.GetLeftPart([System.UriPartial]::Authority)
$headers = @{ Origin = $origin; 'X-Storefront-Request' = 'same-origin' }
$report = [ordered]@{
    checkedAt = [DateTimeOffset]::UtcNow.ToString('o')
    origin = $origin
    homeStatus = $null
    stores = @()
    customerRouteChecks = @()
    errors = @()
}

if ($CheckLocalRuntime) {
    if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') {
        throw 'Local runtime checks require Windows.'
    }
    $serverTask = Get-ScheduledTask -TaskName 'SmartStockServerService' -ErrorAction SilentlyContinue
    $tunnelTask = Get-ScheduledTask -TaskName 'SmartStockStorefrontTunnel' -ErrorAction SilentlyContinue
    $serverAction = if ($serverTask) { @($serverTask.Actions)[0] } else { $null }
    $protectedSettings = Test-Path -LiteralPath (Join-Path $env:USERPROFILE '.smartstock\storefront-tunnel\website-config.dpapi')
    $websiteListener = [bool](Get-NetTCPConnection -LocalPort 8449 -State Listen -ErrorAction SilentlyContinue)
    $connectorCount = @(Get-CimInstance Win32_Process -Filter "Name='cloudflared.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match '(?i)storefront-tunnel' }).Count
    $report.localRuntime = [ordered]@{
        serverTaskRunning = [bool]($serverTask -and $serverTask.State -eq 'Running')
        serverUsesStorefrontLauncher = [bool]($serverAction -and ($serverAction.Execute -match '(?i)storefront' -or $serverAction.Arguments -match '(?i)storefront'))
        protectedSettingsPresent = $protectedSettings
        websiteListener8449 = $websiteListener
        tunnelTaskState = if ($tunnelTask) { [string]$tunnelTask.State } else { 'Missing' }
        tunnelConnectorCount = $connectorCount
    }
    if (-not $websiteListener) { $report.errors += 'Local storefront listener 8449 is absent.' }
    if ($protectedSettings -and -not $report.localRuntime.serverUsesStorefrontLauncher -and -not $websiteListener) {
        $report.errors += 'Protected website settings exist, but the installed server has no storefront listener; verify its installed version and task user.'
    }
    if ($connectorCount -eq 0) { $report.errors += 'Dedicated storefront connector is not running.' }
}

try {
    $home = Invoke-WebRequest "$origin/shop/" -UseBasicParsing -TimeoutSec 12
    $report.homeStatus = [int]$home.StatusCode
} catch {
    $report.homeStatus = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $report.errors += "Home page unavailable (HTTP $($report.homeStatus))."
}

try {
    $directory = Invoke-RestMethod "$origin/shop/api/v1/stores" -Method Post -Headers $headers -ContentType 'application/json' -Body '{}' -TimeoutSec 12
    foreach ($store in @($directory.stores)) {
        $entry = [ordered]@{
            id = $store.id
            name = $store.name
            address = $store.address
            capturedAt = $store.capturedAt
            browseOnly = $null
            campaign = $null
            unavailableServices = @()
            products = @()
            projects = @()
            catalogError = $null
        }
        try {
            $body = @{ storeId = [int]$store.id } | ConvertTo-Json -Compress
            $catalog = Invoke-RestMethod "$origin/shop/api/v1/catalog" -Method Post -Headers $headers -ContentType 'application/json' -Body $body -TimeoutSec 12
            $entry.browseOnly = $catalog.browseOnly
            $entry.capturedAt = $catalog.capturedAt
            $entry.campaign = if ($catalog.campaign) { [ordered]@{ headline = $catalog.campaign.headline; topic = $catalog.campaign.topic; primaryAction = $catalog.campaign.primaryAction; secondaryAction = $catalog.campaign.secondaryAction } } else { $null }
            $entry.unavailableServices = @($catalog.unavailableServices)
            $entry.products = @($catalog.products | ForEach-Object { [ordered]@{ id = $_.id; name = $_.name; sku = $_.sku; category = $_.category; price = $_.price; regularPrice = $_.regularPrice; availability = $_.availability; image = [bool]$_.image; canOrder = $_.canOrder } })
            $entry.projects = @($catalog.projects | ForEach-Object { [ordered]@{ id = $_.id; title = $_.title; category = $_.category; featured = $_.featured; cover = [bool]$_.cover; galleryCount = @($_.gallery).Count } })
            if ($catalog.browseOnly -ne $true) { $report.errors += "Store $($store.id) is missing the browse-only catalog flag." }
            if ($catalog.settings.enabled -ne $false) { $report.errors += "Store $($store.id) publicly reports online ordering enabled." }
            if (@($catalog.products | Where-Object { $_.canOrder }).Count -gt 0) { $report.errors += "Store $($store.id) still offers an orderable product." }
        } catch {
            $entry.catalogError = $_.Exception.Message
            $report.errors += "Store $($store.id) catalog unavailable."
        }
        $report.stores += $entry
    }
    if ($report.stores.Count -eq 0) { $report.errors += 'No public stores were returned.' }
} catch {
    $report.errors += "Store directory unavailable: $($_.Exception.Message)"
}

# GET probes cannot submit customer data and can verify the gateway guard
# before the store origin is made reachable. The exact response text prevents
# an unrelated origin outage from being mistaken for a browse-only rejection.
$expectedMessage = 'Online accounts and requests are not available yet. Browse the Deckers website or contact a store.'
foreach ($route in @('auth/session', 'account', 'favorite', 'custom-quote',
        'custom-quote-file', 'proof-decision', 'quote', 'checkout')) {
    try {
        $response = Invoke-WebRequest "$origin/shop/api/v1/$route" -Method Get `
            -TimeoutSec 12 -SkipHttpErrorCheck
        $message = try { ($response.Content | ConvertFrom-Json).message } catch { $null }
        $blocked = [int]$response.StatusCode -eq 503 -and $message -eq $expectedMessage
        $report.customerRouteChecks += [ordered]@{
            route = $route
            status = [int]$response.StatusCode
            blockedByBrowseOnlyGateway = $blocked
        }
        if (-not $blocked) { $report.errors += "Customer route $route was not rejected by the browse-only gateway." }
    } catch {
        $report.errors += "Customer route $route could not be checked: $($_.Exception.Message)"
    }
}

$json = $report | ConvertTo-Json -Depth 12
if ($OutputPath) { [System.IO.File]::WriteAllText($OutputPath, $json, [System.Text.UTF8Encoding]::new($false)) }
$json
if ($report.errors.Count -gt 0) { exit 1 }
