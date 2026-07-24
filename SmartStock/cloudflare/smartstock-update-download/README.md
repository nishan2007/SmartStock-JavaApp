# SmartStock private update downloads

This Worker streams private SmartStock update ZIPs from the
`smartstock-updates` R2 bucket. The SmartStock LAN/server service creates a
short-lived HMAC-signed URL after authenticating the register and employee
session. Cloudflare credentials and the signing secret are never included in
the desktop application.

## One-time Cloudflare setup

```bash
npm install
npx wrangler login
npx wrangler r2 bucket create smartstock-updates --location=enam
./setup-service.sh /absolute/path/to/.smartstock/profiles/development
```

Use the same randomly generated secret on the SmartStock server:

```text
SMARTSTOCK_UPDATE_R2_SIGNING_SECRET=<same secret, at least 32 characters>
SMARTSTOCK_UPDATE_R2_WORKER_URL=https://smartstock-update-download.<account-subdomain>.workers.dev
```

Restart the SmartStock server/sync service after changing its environment.
The setup may alternatively save both values in the active, owner-only profile
file at `~/.smartstock/profiles/<environment>/r2-update.properties`.

## Release metadata convention

R2-backed rows in Supabase `app_releases` use:

```text
artifact_bucket = r2:smartstock-updates
artifact_path   = <the exact R2 object key>
```

Existing rows using `artifact_bucket = smartstock-releases` continue to use
private Supabase Storage signed URLs.

## Publish an update

From the `SmartStock` directory, provide the Supabase server credential only to
the release operator's shell:

```bash
SUPABASE_URL="https://<project>.supabase.co" \
SUPABASE_SECRET_KEY="<server secret>" \
./tools/publish-r2-update.sh \
  target/release-mac/smartstock-mac-1.0.27.zip \
  1.0.27 10027 mac release-notes.txt
```

The script uploads the ZIP, downloads it again, verifies its byte size and
SHA-256, and only then publishes the `app_releases` row. It never writes the
Supabase server key or Cloudflare credentials into the application package.
