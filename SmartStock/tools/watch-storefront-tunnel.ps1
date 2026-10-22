param([string]$TunnelDirectory=(Join-Path $env:USERPROFILE '.smartstock/storefront-tunnel'),[string]$Cloudflared='cloudflared.exe')
$ErrorActionPreference='Stop'
$mutex=[Threading.Mutex]::new($false,'Local\DeckersStorefrontTunnelWatch')
if(!$mutex.WaitOne(0)){$mutex.Dispose();exit 0}
try{
 while($true){
  try{
   $pidFile=Join-Path $TunnelDirectory 'connector.pid';$alive=$false
   if(Test-Path -LiteralPath $pidFile){$connectorId=0;if([int]::TryParse((Get-Content -Raw -LiteralPath $pidFile).Trim(),[ref]$connectorId)){$process=Get-CimInstance Win32_Process -Filter "ProcessId=$connectorId" -ErrorAction SilentlyContinue;$alive=$process -and $process.Name -eq 'cloudflared.exe' -and $process.CommandLine -like "*$TunnelDirectory*"}}
   if(!$alive){& (Join-Path $TunnelDirectory 'start-storefront-tunnel.ps1') -Cloudflared $Cloudflared -TunnelDirectory $TunnelDirectory | Out-Null}
  }catch{Add-Content -LiteralPath (Join-Path $TunnelDirectory 'watchdog.log') -Value ((Get-Date -Format o)+' Connector retry: '+$_.Exception.GetType().Name)}
  Start-Sleep -Seconds 30
 }
}finally{$mutex.ReleaseMutex();$mutex.Dispose()}
