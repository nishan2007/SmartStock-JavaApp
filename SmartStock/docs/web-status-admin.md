# Web Status in Admin

The Admin **Web Status** tile requires the existing Device Management permission.
The screen uses Deckers panels, accents, fonts, and a dedicated monitor icon. It
refreshes every ten seconds, with manual refresh and an option to pause updates.

It reports the public installer site's connectivity and the registered public
store verification endpoint independently. The latter is the connection that
failed during the October 3 tunnel outage. Health probes never submit a username
or password. Public checks are cached for fifteen seconds and bounded by timeouts.

Service cards cover website/snapshot synchronization and installer authentication,
both installed Cloudflare tunnels, scheduler access/gateway heartbeat, employee
applications, mobile image/item/Studio browser tools, and the main register API.
Fresh website synchronization is distinguished from an unhealthy listener.
The main register API is monitoring-only to avoid disconnecting registers.

The screen shows server JVM uptime, heap usage, process CPU, threads, listener
uptime, aggregate request totals, response bytes, and server-error counts.
Counters reset with the server process, and service uptime resets when its
listener restarts. Requests include health checks. Tunnel cards show process
uptime and CPU time; their public connectivity is verified separately. The
separate scheduler gateway and Cloudflare-hosted download worker do not expose
request totals here. This is operational usage, not Cloudflare billing usage or
visitor analytics. No image data, credentials, or visitor identities are retained.

Controls require an authenticated device/session, Device Management permission,
SERVER mode, a local server address, a non-remote device, and PRIMARY authority.
Employee-application controls additionally require Employee Management.
Registers and remote-admin clients can read status but cannot control services.
Start/Stop/Restart commands accept only fixed service IDs and actions and are
audited as requested, completed, or failed. A bounded process-lifetime command
receipt cache prevents immediate duplicate commands from repeating a restart.

Mobile and scheduler controls reuse existing state/session revocation workflows.
Scheduler restart cycles scheduler access and browser sessions; the separate
gateway process remains managed by its installed service. Website listener stop
lasts until manually started or the server service restarts. Tunnel controls are
Windows-only and use the fixed SmartStockStorefrontTunnel and
SmartStockSchedulerTunnel installed tasks, terminating only matching cloudflared
connector processes. They cannot run user-provided commands or select processes.

This change has not been packaged, published, or installed on the live server.
New tests cover allowed actions, tunnel command allowlists, aggregate metrics,
real HTTP request/error/byte counters, and server-side authorization boundaries.
Full-suite checks currently encounter unrelated cross-store/migration changes.
Installed service control and a paired-device end-to-end check remain deployment
verification steps; no live service was stopped or restarted for UI testing.
