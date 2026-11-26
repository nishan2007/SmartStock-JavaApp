# Shared CT221B USB label printer

SmartStock can share one CT221B per store through the existing authenticated HTTPS
LAN API. Windows printer sharing and inbound ports on the USB computer are not
required. The store server and USB host must both be reachable. This feature
requires the updated application on the server and participating computers.

## Setup

1. On the host, connect the CT221B by USB and install its manufacturer driver.
   Set the driver's paper size to the loaded roll. Keep `CT221B` in its queue name.
2. Sign in with Company Preferences permission. Open Workstation Preferences →
   Hardware Settings → **Shared CT221B Setup / Status**.
3. Select the local CT221B queue, name the host, and select **Share from this
   computer**. This also selects the shared route on the host. Wait for Online,
   then select **Test shared label** and inspect it.
4. On each computer that should print there, open the same screen, select
   **Use the store's shared CT221B on this computer**, and select **Save this
   computer**.
5. Print an order label and a price-tag batch from each computer. Existing price
   templates, including two-across CT221B gap calibration, are retained.

Keep SmartStock running on the host. After the host restarts, open SmartStock and
sign in once so its main menu starts the worker. The worker continues while the
application remains open; it does not require the setup window to stay open.
Driver installation, USB connectivity and physical output require verification on
the host. SmartStock manages sharing and routing; the OS driver supplies USB access.

## Job status and recovery

- `QUEUED`: waiting for the configured host.
- `SENDING`: handed to the host. The physical result may be uncertain after a
  disconnect, lost response, application crash or spooler error.
- `SPOOLED`: accepted by the host driver, which does not prove a label emerged.
- `FAILED`: host reported a print error. Check the printer before reprinting.
- `UNKNOWN`: a claimed job remained unresolved for ten minutes. Check output
  before submitting a replacement.
- `EXPIRED`: unclaimed for ten minutes; it will not be printed later.
- `CANCELLED`: sharing stopped or the host/USB queue changed before the job was
  claimed. Already claimed jobs can still finish on their original host.

Jobs are stored locally under the active store profile's `shared-label-printer`
folder, protected with owner-only permissions and removed on the next queue access
after 24 hours. They do
not use cloud storage or a new database schema. Pending jobs survive a server
restart; claimed jobs are never automatically replayed. A new server process
requires a fresh host heartbeat before accepting submissions. A submission with a
lost response displays its job ID so it can be checked before submitting again.

Only the enrolled device selected by an employee with Company Preferences
permission can claim jobs for its store. Submission and status require an employee
session and an existing sales, inventory or custom-order permission. Remote Admin
and SmartStudio devices are excluded. Job payloads contain bounded PNG label
rasters and numeric dimensions; callers cannot send arbitrary printer commands.

## Physical acceptance

Verify USB output, stock dimensions, gap alignment, two-across tags, order-label
copies and barcode readability. Test with a second SmartStock computer, an offline
host, a host restart, a server restart and a disconnected USB cable. Confirm no
duplicate labels after interrupted delivery. Windows services, installed-app
updates, cash drawers, NFC and live database workflows are separate checks.
