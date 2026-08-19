# OneDrive product image storage

SmartStock keeps product and custom-item images in the store server's local image mirror. OneDrive is a deferred cloud copy used by other store servers and for recovery; registers continue to fetch images from their paired server over the authenticated LAN API.

## Microsoft 365 preparation

1. Provision a dedicated licensed Microsoft 365 business user and open OneDrive once so its drive exists.
2. Register a single-tenant Entra application.
3. Grant and admin-consent the Microsoft Graph application permission `Files.ReadWrite.AppFolder`.
4. Create an RSA certificate credential. Keep the private key in unencrypted PKCS#8 PEM form only long enough to load it into the SmartStock server's secure credential store; protect and then remove the staging file.

## Server configuration and rollout

Run these commands only on the SmartStock server, with its normal server profile active:

```sh
mvn -q -f SmartStock/pom.xml exec:java \
  -Dexec.mainClass=services.ServerImageAssetMaintenance \
  -Dexec.args="configure TENANT_ID CLIENT_ID USER_UPN /secure/app-cert.pem /secure/app-key-pkcs8.pem"

mvn -q -f SmartStock/pom.xml exec:java \
  -Dexec.mainClass=services.ServerImageAssetMaintenance -Dexec.args="probe"

mvn -q -f SmartStock/pom.xml exec:java \
  -Dexec.mainClass=services.ServerImageAssetMaintenance -Dexec.args="begin"

mvn -q -f SmartStock/pom.xml exec:java \
  -Dexec.mainClass=services.ServerImageAssetMaintenance -Dexec.args="sync"
```

Repeat `sync` until Company Preferences reports zero migration items pending. Then activate the provider:

```sh
mvn -q -f SmartStock/pom.xml exec:java \
  -Dexec.mainClass=services.ServerImageAssetMaintenance -Dexec.args="activate"
```

Use `rollback` to return scoped image reads and writes to Supabase during the retained rollback window. Do not remove Supabase copies until every store server has synchronized successfully and live cross-store retrieval has been accepted.

After that acceptance, remove rollback copies individually with `cleanup-supabase <asset-uuid> I_HAVE_VERIFIED_ALL_STORES`. The command is deliberately per asset, works only in the active OneDrive phase, and reverifies the OneDrive SHA-256 before deleting the Supabase object. There is no automatic bulk deletion.

The configuration command stores the tenant, client, user, certificate, and private key through `SecureCredentialStore`. Do not put these values in repository configuration, register installations, scripts, or support bundles.
