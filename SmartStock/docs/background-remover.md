# Staff background remover

Open **Status → Mobile Item Web App** on a register, activate the browser using the existing QR code, and sign in. Choose **Photo studio · Remove an image background** on the mobile home page. The direct path is /background-remover.html on the same secure mobile web address.

The page uses Company Preferences identity (name, logo when available, motto, phone and email) and the Deckers palette. Upload a PNG or JPEG, choose Remove background, and download the transparent PNG. Original dimensions are preserved. Inputs must be 6 MB or smaller, at least 32 pixels in each direction, and no larger than 12 megapixels.

The server requires an active mobile session, CSRF validation, and one of NEW_ITEM, EDIT_ITEM, MANAGE_CUSTOM_ORDER_ITEMS, CUSTOM_ORDER_ITEMS or MANAGE_CUSTOM_ORDERS. Only one background-removal request runs at a time. Inputs and outputs are held in memory and are not saved to the catalog or cloud.

Processing uses SmartStock's existing ONNX IS-Net model locally. No Python service or internet request is needed for processing. The server installation must include dependency/catalog-studio/isnet-general-use.onnx beside its application jar, with the existing verified SHA-256. Missing or mismatched models report an error rather than downloading at runtime. A logo stored only in cloud storage may be unavailable offline; company text still appears.

Validation: run the normal Maven and security gates. To exercise actual offline inference, supply -Dsmartstock.test.photoModel=<absolute packaged model path> with -Dtest=BackgroundRemovalModelTest. Before rollout, verify the updated installed server, browser activation/sign-in, company identity, staff permission denial, upload/preview/download, and cutout quality on real product photos. No database schema change is needed.

## Computers and a private Deckers address

The web app supports desktop browsers as well as phones. The status dialog includes Open in browser and Copy activation link; each new browser needs its own fresh activation link.

For studio.deckers.gy, create a local DNS A record mapping that exact name to the server's reserved LAN address. Leave public DNS, public website routing and port forwards unchanged. Configure only the subdomain, not the deckers.gy zone, so the public shop continues resolving normally. On the server run tools/configure-local-web-address.ps1 under the account whose user home is used by SmartStock; ServerUserHome can explicitly name that account's home directory. Stop and start the web app after DNS is ready. The address is https://studio.deckers.gy:8444/background-remover.html.

This creates a separate mobile-web-studio.deckers.gy.p12 and a public mobile-web-studio.deckers.gy.cer under that account's .smartstock directory. Register pairing and its existing lan-api.p12 certificate stay intact. Staff browsers must trust the public certificate after verifying its fingerprint through the store administrator. Never distribute the .p12 or its password.

Public peer addresses and forwarded proxy requests are rejected. Do not connect this service to a public tunnel or forward its ports. Local DNS resolution and LAN firewall reachability are separate setup steps. Router DNS settings differ by firmware; manual upstream DNS is not necessarily a local hostname record editor. If the Cudy firmware has no hostname-record editor, use a local DNS resolver with an exact-name override and configure the router/client DNS to use it.

On the inspected Cudy R700 V1.0, firmware 2.5.4, Custom DNS showed upstream DNS options and DHCP Settings showed Preferred/Alternate DNS. Neither inspected form exposed local hostname records. Router settings were not modified during this inspection.

## Store DNS deployment, 2026-10-01

CoreDNS 1.14.7 was installed in C:\ProgramData\SmartStockLocalDns as the SYSTEM startup task SmartStockLocalDns. The official Windows archive SHA-256 was verified as 6b718a5ed57d7034b1e4c3df500121307830da291d15cf8387f41c297c6fdd56. The installed executable SHA-256 is a6ac3466a93f907c5ef7c170a41fdc6901df195da223c60adeca8de1a891d8b5.

It listens only on 10.1.1.221:53 and maps studio.deckers.gy to 10.1.1.221. All other names are forwarded to the existing router DNS at 10.1.1.1. UDP and TCP DNS checks passed, as did public www.deckers.gy and example.com lookups. Existing Windows Internet Connection Sharing was preserved. Firewall rules allow DNS only from 10.1.1.0/24 to this address/program.

Cudy DHCP Settings should have Preferred DNS 10.1.1.221 and no Alternate DNS. Public alternate resolvers cannot answer the private studio name consistently. Router upstream DNS remains unchanged. Reconnect clients to renew DHCP after the setting is applied. DNS is an essential network dependency once this is applied: the store server must stay on. To roll back, restore the prior empty Preferred/Alternate DNS fields on the router, renew clients, then an administrator can stop/disable the SmartStockLocalDns scheduled task and disable its two firewall rules. Do not disable Windows Internet Connection Sharing.

Server user C:\Users\decke has mobile-web.properties configured for studio.deckers.gy. A separate browser certificate was generated with SHA-256 b4f055b8a94558690f42efae934187414917f346d0ffcecd4e1908a1fd459cd7. The existing register keystore hash was verified unchanged. Windows browser certificate trust confirmation and router re-login were still pending at preparation time. App update 1.0.233 is required because the installed 1.0.232 lacks the new browser SSL context.

## Completion status, 2026-10-01

The Cudy router DHCP Preferred DNS was set to 10.1.1.221 and verified after a full reload; Alternate DNS remains empty. The public DNS/upstream DNS settings were preserved. A router settings screenshot is saved under target/local-dns-setup/router-dns-confirmed.png.

SmartStock 1.0.233 update ZIP was published after remote download/size/SHA-256 verification. Its size is 296615609 bytes and SHA-256 is 732a8add576cea6ef35f838f73b180e3c594fc0707ebdde9c07d6afe1183dafe. The existing updater completed successfully and installed 1.0.233 in both Program Files and the user-owned server copy, retaining a rollback copy. The separate studio certificate was trusted in this user's Windows certificate store. An HTTPS request addressed as studio.deckers.gy to the server's LAN IP returned HTTP 200 with certificate validation enabled.

Other clients must reconnect/renew DHCP and trust the store's public browser certificate. Existing static/multiple-adapter DNS on the server requires an exact-host NRPT rule for studio.deckers.gy using 10.1.1.221; Windows administrator approval for that rule was pending when this status was written. A separate browser image upload/download and another physical client have not yet been verified.

Final verification: the exact-host Windows DNS rule was approved and studio.deckers.gy resolved through the computer's normal resolver to 10.1.1.221. A normal HTTPS request without an address override returned HTTP 200. The private studio URL opened in the in-app browser without a certificate warning and displayed the expected staff activation requirement. Browser proof: target/local-dns-setup/studio-live.png. The tab was left open for the user. Authenticated upload/download and acceptance from a second physical device remain to be checked.
