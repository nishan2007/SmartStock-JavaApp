[CmdletBinding(SupportsShouldProcess)]
param([Parameter(Mandatory)][string]$SettingsPath)
$ErrorActionPreference='Stop'
$settings=Get-Content -LiteralPath $SettingsPath -Raw|ConvertFrom-Json
foreach($name in @('oldShare','newShare')){
    if($settings.$name -notmatch '^[A-Za-z][A-Za-z0-9_-]{0,39}$'){throw 'Invalid company share name.'}
}
if($settings.server -notmatch '^[A-Za-z][A-Za-z0-9-]{0,14}$' -or
    -not $settings.displayName -or $settings.displayName.Length -gt 80 -or $settings.displayName -match '[\x00-\x1f\x7f]'){
    throw 'Invalid workstation branding settings.'
}
$url=$null
if(-not [uri]::TryCreate($settings.url,[UriKind]::Absolute,[ref]$url) -or $url.Scheme -ne 'https' -or $url.UserInfo -or
    $url.Fragment -or $settings.url -match '["\r\n]'){throw 'Use the configured HTTPS management URL.'}
$old='\\'+$settings.server+'\'+$settings.oldShare;$new='\\'+$settings.server+'\'+$settings.newShare
$mappings=@(Get-SmbMapping|Where-Object {$_.RemotePath -and $_.RemotePath.TrimEnd('\') -ieq $old -and $_.LocalPath})
if(@(Get-SmbConnection|Where-Object {$_.ServerName -ieq $settings.server -and $_.ShareName -ieq $settings.oldShare -and $_.NumOpens -gt 0}).Count){
    throw 'Close files and Explorer windows using the old drive before updating this workstation.'
}
$desktop=[Environment]::GetFolderPath('Desktop')
$oldShortcut=Join-Path $desktop ($settings.oldShare+' Management.lnk')
$newShortcut=Join-Path $desktop ($settings.newShare+' Management.lnk')
$launcher=Join-Path $PSScriptRoot 'Open-Management.ps1'
$powershell=Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$shortcut=$null
if(Test-Path -LiteralPath $oldShortcut){
    $shell=New-Object -ComObject WScript.Shell;$shortcut=$shell.CreateShortcut($oldShortcut)
    if($shortcut.TargetPath -ine $powershell -or $shortcut.WorkingDirectory -ine $PSScriptRoot -or
        -not $shortcut.Arguments.Contains('"'+$launcher+'"')){throw 'The existing shortcut is not owned by these SmartDrive tools. Review it separately.'}
}
if($oldShortcut -ine $newShortcut -and (Test-Path -LiteralPath $newShortcut)){throw 'The new management shortcut already exists. Review it before updating.'}
if($PSCmdlet.ShouldProcess($settings.server,'Update persistent drive mappings and the company management shortcut using existing credentials')){
    if(-not (Test-Path -LiteralPath $new -PathType Container)){throw 'The new share is unavailable with the saved workstation credential. Keep the old mapping and contact the server manager.'}
    if($old -ine $new){foreach($mapping in $mappings){
        Remove-SmbMapping -LocalPath $mapping.LocalPath -UpdateProfile -Force -Confirm:$false
        try{New-SmbMapping -LocalPath $mapping.LocalPath -RemotePath $new -Persistent $true|Out-Null}
        catch{
            try{New-SmbMapping -LocalPath $mapping.LocalPath -RemotePath $old -Persistent $true|Out-Null}
            catch{throw 'The update and restoration of the old mapping failed. The saved credential remains; ask the server manager to reconnect the drive.'}
            throw 'The new mapping failed. The original mapping has been restored.'
        }
    }}
    if($shortcut){
        $shortcut.Description=$settings.displayName+' management'
        $guest=if($shortcut.Arguments -match '(?:^| )-StartLocalService(?: |$)'){' -StartLocalService'}else{''}
        $shortcut.Arguments='-NoProfile -File "'+$launcher+'" -Url "'+$url.AbsoluteUri+'"'+$guest
        $shortcut.Save()
        if($oldShortcut -ine $newShortcut){Rename-Item -LiteralPath $oldShortcut -NewName ([IO.Path]::GetFileName($newShortcut))}
    }else{
        # Use the same trusted launcher and current profile as normal shortcut setup.
        $shell=New-Object -ComObject WScript.Shell;$shortcut=$shell.CreateShortcut($newShortcut)
        $shortcut.TargetPath=$powershell;$shortcut.WorkingDirectory=$PSScriptRoot
        $shortcut.Arguments='-NoProfile -File "'+$launcher+'" -Url "'+$url.AbsoluteUri+'"'
        $shortcut.Description=$settings.displayName+' management';$shortcut.Save()
    }
    Write-Output "Company drive and management shortcut updated for this Windows profile: $new"
}
