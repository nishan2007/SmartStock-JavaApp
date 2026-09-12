# Employment application portal

The portal extends Employee Management > Applications. Applicants sign in, save a general application, optionally attach documents, submit, and track manager updates. The three steps are Personal details (including optional areas of interest and introduction), Education & experience, and Documents & review. Date of birth, name, contact details, and the declaration are required at submission. Identification is optional. Hiring keeps the same Supabase Auth UUID and password, and creates an active employee only after the manager completes existing employment settings.

## Deployment

The installed SmartStock background server owns the HTTPS listener on **127.0.0.1:8448**. Publish only `/register` and `/register/` through a permanent HTTPS reverse proxy or named tunnel. PostgreSQL and register `/v1/` routes must remain private. This is independent of the scheduler listener and its request size limits.

Set `public.origin=https://jobs.your-company.example` in the active environment's `applications.properties` (production: `%USERPROFILE%/.smartstock/applications.properties`; use the actual service account's environment directory). Alternatively configure `SMARTSTOCK_APPLICATION_PUBLIC_ORIGIN`. Configuration and tunnel credentials are machine-local and must not be committed or packaged. A permanent hostname is required; this feature does not create a temporary tunnel.

The proxy must preserve the public Host, overwrite `X-Real-IP` with the actual client address, use the provisioned LAN certificate trust for upstream HTTPS, limit request bodies to 14 MiB, and apply per-IP login and upload limits. Only the trusted loopback proxy can reach the listener. Do not expose port 8448 directly. Allow at least 120 seconds for document requests. For Cloudflare Tunnel, set the upstream HTTP Host to the configured public origin's authority and configure the upstream CA/certificate name; do not disable TLS verification.

Start and stop from the existing employee registration gateway control in Employee Management. Its dialog provides the public link, QR code, copy-link, and public health check. Stop/restart clears all portal sessions. The saved enabled state is restored by the background server. Remote access requires the active store server, PostgreSQL, Auth, and internet connectivity.

Apply the ordered `20260909120000_employment_portal.sql` migration through the existing local and cloud provisioning workflows. Local automatic upgrades refuse schema drift. The cloud migration creates the private `employment-applications` bucket (10 MiB/object, JPEG/PNG/PDF), denies direct browser access even if a broader Storage policy exists, and restricts authenticated Storage writes to active employees. The server accesses the bucket with its existing secure credentials. No new key is sent to registers or the browser.

## Auth and email setup

### SmartStock Gmail delivery (Send Email hook)

SmartStock can deliver Supabase-generated codes through the store's existing Gmail OAuth connection. Supabase still creates and verifies codes and manages passwords and identities. Configure Gmail for the primary server's location first, then use Company Preferences > Application portal > Configure verification emails. Store the Supabase hook signing secret there; it is encrypted in the server account's credential store and is never returned to the client.

The HTTPS Send Email hook URL is `https://careers.deckers.gy/register/hooks/send-email` (replace the hostname for other deployments). Install and restart the updated server, save its signing secret, verify the Gmail connection and a signed delivery, then enable the Send Email HTTP hook in Supabase Authentication > Hooks. Do not enable the hook before its receiver is ready. This replaces Supabase email delivery for the **whole Auth project**. Keep the portal and store server running; disabling the portal also disables this project's authentication email delivery. To roll back, disable the Supabase hook first so built-in delivery resumes.

Portal confirmation and recovery messages contain a code to enter on the page. Other supported Auth actions retain verification links when required, including both recipients for secure email changes. Company name is loaded from saved branding for each message. When this hook is enabled, the template/SMTP instructions below are an alternative and do not control these messages.

Requests require a fresh Standard Webhooks HMAC signature over the exact body. Delivery workers and request sizes are bounded. Success is returned only after Gmail confirms sending; failures and timeouts surface as retryable account errors. Supabase gives HTTP hooks five seconds, so verify live delivery latency before launch. Receipt files contain only fingerprints to suppress completed retries across restarts. A crash or timeout after Gmail accepts a message but before a receipt is saved can cause a duplicate on retry. No delivery codes, email bodies, or recipients are stored in these receipts.

On the code-entry screen, **Send a new code** retries confirmation or recovery; previously delivered links and codes are unchanged by installing this update.

The code-entry flow requires a live Auth email-template change; installing the desktop application does not update Supabase templates. Use `portal-confirmation-email.html` for Confirm signup and `portal-recovery-email.html` for Reset password after checking other clients that share the project. Both include `{{ .Token }}` and avoid consuming the code through a confirmation link. Preserve existing template behavior for other clients where needed. Read back the saved templates, then test a newly requested email and code verification. Emails already delivered are unchanged. Allow `https://careers.deckers.gy/register` in the production redirect list without removing existing client redirects.

Enable email/password signup and email confirmation in the existing Supabase project. Configure production SMTP and allow the permanent `https://jobs.your-company.example/register` redirect URL. Configure **Confirm signup** and **Reset password** email templates to include `{{ .Token }}` as a verification code, with the public application link. This portal intentionally uses codes entered into the page, so tokens do not appear in URLs or browser storage. Branded email templates are separate from the live page branding; maintain them in the email provider.

Verify email delivery before publishing the link. Signup/recovery emails are included; recruitment status notifications are shown in the portal only. Public signup does not create an employee or assign roles. Existing pending/rejected registration bans are reconciled only when the local Auth UUID, email, and immutable registration app metadata agree. Unrelated account bans are not cleared. Incomplete legacy SUBMITTING records require manager reconciliation; they are not automatically adopted.

## Data and API

- Same-origin POST `/register/api/auth/{signup,verify,login,recover,reset,session,logout}` uses server-side Auth calls; the browser receives only an opaque Secure HttpOnly cookie and CSRF token.
- Same-origin POST `/register/api/{application,save,submit,withdraw,upload,remove}` checks the current Auth identity and application ownership. Save/submit require the expected revision. All writes require the session CSRF header and `X-Registration-Request: same-origin`.
- GET `/register/attachments/{uuid}` checks ownership before streaming private files. Metadata never reveals the storage object path. Managers use permission-checked LAN application routes instead.
- GET `/register/branding` exposes only company identity/contact fields and a same-origin logo URL. Branding reloads on navigation and every 45 seconds, including company changes saved in SmartStock.
- GET `/register/health` reports active-server state; the full application depends on database and Auth availability as well.

Drafts, immutable submitted revisions, attachments, and public/internal review messages are stored locally and covered by the recovery mirror manifest. They do not enter cross-store reference exchange. Internal notes are omitted from applicant responses. Drafts are excluded from the normal manager listing. Managers may reopen rejected or withdrawn applications by requesting information. Hired/partially activated records cannot be reopened through review actions.

Each application has up to 10 active documents, 10 MiB/file and 50 MiB total. Images are decoded and re-encoded to remove metadata, with a 40-megapixel ceiling. PDFs must be unencrypted and at most 20 pages. Document mutations share the application lock with submission/review. Removing a document hides it from the applicant but preserves stored evidence and submitted revision references; no automatic retention purge is configured. Manager document previews are downloaded into an owner-restricted environment cache. Only an explicitly categorized identification attachment may populate the employee ID document field on hiring.

## Verification

Run `mvn -q -f SmartStock/pom.xml test`, the Bash security check, and `git diff --check`. Browser fixture checks: `node SmartStock/tools/test-employment-portal.cjs` with Playwright available in `NODE_PATH`; screenshots are written to `SmartStock/target/portal-browser`. These fixtures do not prove live Auth/Storage behavior.

The PostgreSQL integration test is opt-in and installs a baseline into an explicitly disposable `portal_test` database on loopback with user `portal_test`: pass `-Dsmartstock.portal.test.jdbc=jdbc:postgresql://127.0.0.1:PORT/portal_test` and select `EmploymentApplicationIntegrationTest`. Never point it at a store database.

Before launch: rehearse migration/backup restoration; verify signup, codes, reset, private files, account separation, hiring, permission revocation, saved branding, installed-service restart, and cellular access. Test slow/interrupted uploads and server outages. A successful local build is not evidence that the public hostname, SMTP, cloud policies, or installed service have been configured. Rollback should be a forward repair; never restore over live transactions automatically.
