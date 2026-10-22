# Windows store network maintenance

Version 1.1.30 (build 101030) installs network maintenance during elevated Windows server update/setup or Repair LAN Firewall. Registers do not install the SYSTEM task. Each server preserves its existing LAN firewall scope (default LocalSubnet) and existing DNS Corefile; no store IP, router IP, register list or credentials are included in the release.

The protected ProgramData/SmartStockNetwork task runs at startup and every minute. It repairs its own missing, disabled or altered Private-profile inbound rules for HTTPS 8443, discovery UDP 18443, mobile 8444/8445 and employee registration 8448. It does not disable Windows Firewall, change network categories, remove administrator block rules or override Group Policy. Keep the register-facing adapter Private; Public classification or overriding policy requires administrator review.

Local DNS is optional. If installed through the verified CoreDNS 1.14.7 installer, the maintenance installer verifies the original archive and executable, protects its directory, preserves its Corefile, and installs SmartStockLocalDns startup/restart. TCP/UDP DNS access is restricted to the configured bind address and store LAN scope. A modified executable requires administrator verification. No DNS software is downloaded or DNS addresses changed by an app update.

To repair an existing server before updating, run `src/windows/network-maintenance.ps1 -Install` from an elevated PowerShell in the repository on that server. Use `-LanSubnet <private CIDR>` for a managed VLAN. It saves settings outside application/update/rollback folders. Logs are in `%ProgramData%\SmartStockNetwork\maintenance.log`. Do not run it on registers.

Automatic repair cannot compensate for an incorrect server hostname, changing DHCP addresses, router client isolation or disconnected cables. Reserve each server's LAN address at its router and ensure its computer name and .local alias resolve to that address on registers. Do not copy this store's addresses to other stores.

Acceptance before rollout: on an isolated Windows server, install maintenance, verify task and directory ACLs, disable/delete/change each managed firewall rule and confirm repair within a minute; stop CoreDNS and confirm task restart; reboot and verify DNS and server reachability from a register. Verify Public adapters remain inaccessible and custom LAN scopes are retained. Test update/rollback and register-only installation. No publication or live application installation is authorized by preparing these artifacts.
