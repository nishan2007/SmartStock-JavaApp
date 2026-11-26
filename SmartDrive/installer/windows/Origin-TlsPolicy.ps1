function Set-OriginCertificateAccess([string]$Path) {
    $acl=New-Object Security.AccessControl.FileSecurity
    $acl.SetAccessRuleProtection($true,$false)
    foreach($sid in @('S-1-5-18','S-1-5-32-544')){
        $identity=New-Object Security.Principal.SecurityIdentifier($sid)
        $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($identity,'FullControl','Allow')))
    }
    $service=New-Object Security.Principal.SecurityIdentifier('S-1-5-19')
    $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($service,'Read','Allow')))
    Set-Acl -LiteralPath $Path -AclObject $acl
}
function Get-OriginFirewallPolicy($Origin,[string]$NodePath) {
    @{Name='SmartDrive-Origin';Description='Managed by SmartDrive origin TLS setup';Enabled='True';Direction='Inbound';Action='Allow';
        Protocol='TCP';LocalAddress=[string]$Origin.listenAddress;LocalPort=[int]$Origin.port;RemoteAddress=@($Origin.allowedAddresses);
        Profile=@('Private','Domain');Program=$NodePath}
}
