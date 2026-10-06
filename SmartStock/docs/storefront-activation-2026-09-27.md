# Website activation — 2026-09-27

The user authorized configuring and restarting the installed background server.
The existing SmartStockServerService scheduled task was updated through Windows
elevation to a launcher that decrypts website settings for its child process.
The original task/action backup and protected settings are outside the repository
in the current user's `.smartstock/storefront-tunnel` directory. No keys or store
identity manifests are included here.

Verified after restart:

- Register 8443, scheduler 8446, careers 8448 and website 8449 are listening.
- Website health passed with certificate validation and matched the configured
  registered server identity and store location.
- Cloudflare Worker `smartstock-storefront` deployed to www.deckers.gy and
  deckers.gy. Apex navigation redirects to www with HTTP 308.
- With the user's explicit confirmation, saved the proxied storefront-origin
  CNAME pointing to the dedicated deckers-storefront tunnel.
- Public website returns HTTP 200; direct unauthenticated origin access returns
  403; anonymous account access returns 401; quotes remain disabled with 503.
- Browser shows the saved Deckers logo, company motto/contact details and Skeldon
  store. Catalog reports zero published products and ordering disabled.
- Fixed gateway store discovery to allow browsing while ordering is disabled;
  all 24 Worker tests passed and the gateway fix was deployed. This needed no
  replacement of the installed Java package.

The SmartStockStorefrontTunnel task starts the dedicated connector at this user's
Windows logon. This is not unattended pre-logon startup; reboot/logon behavior
has not been rehearsed. Existing scheduler/careers routing remains unchanged.
The current server launcher wraps the saved 1.0.220 action. Future in-app updates
may replace that scheduled-task action; recheck website startup configuration
after any update rather than assuming it persists.

Products must be explicitly published in Company Preferences → Online Store.
Keep ordering disabled until real customer Auth/email, installed operator flows,
cellular access and multi-server failure/recovery acceptance are complete. Only
one store origin is currently configured, so there is no deployed backup server.
The outstanding limitations in storefront-readiness.md still apply. No test
customer, sale, checkout, or email was created during activation checks.
