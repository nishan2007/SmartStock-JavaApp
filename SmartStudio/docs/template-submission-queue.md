# Organization template submissions

Published for Windows in SmartStock 1.1.54 and SmartStudio 0.1.49 on
19 October 2026. The storefront gateway is deployed. Upgrade the local
SmartStock server/schema before the Studio client.

Save a shared badge template with **Save print records** enabled. The feature
remains off by default. Bind text layers to entry fields, and give every photo
slot a stable photo ID by saving the template before sharing its link.

**Create / rotate organization link** creates a private organization URL. The
first person opening it chooses and confirms an access code (8–128 characters).
Later visitors enter that code. **Copy organization link** retrieves the same
URL without changing its code or active sessions. Link management requires
owner-store access plus the existing template-management permission.

Every distinct text binding and every photo slot becomes a required form
input. Visitors can upload or take each photo, preview it, and submit one
person at a time. Images are limited to 2 MB / 12 megapixels at the server;
the browser reduces large camera images before upload. The whole submission
is limited to 12 MB. A successful receipt clears the person's inputs and
photos. Network retries reuse a submission UUID; conflicting reuse is rejected.
The website never lists or retrieves submissions or historical print records.

If template fields change while a visitor is entering data, **Refresh template
fields** retains matching text/photo inputs and requires review of the new
fields. **Re-enter organization access code** supports reset/expired sessions
without putting credentials in persistent browser storage.

## Permissions and access-code reset

**Reset template submission access codes** (`RESET_TEMPLATE_QUEUE_CODE`) is a
separate role permission, granted to ADMIN by default. Both the authenticated
LAN route and the queue service enforce it. Owner-store access is also
required. Reset clears the code and increments its generation, invalidating
all previous website sessions. The URL remains stable and the next visitor
chooses a new code. No queued entry or print record is deleted.

**Process template submissions** (`PROCESS_TEMPLATE_SUBMISSIONS`) separately
allows queue review and printing. It does not authorize code resets. ADMIN
receives both permissions. Role management uses the normal permission catalogue.

Codes use salted PBKDF2-HMAC-SHA256 with 600,000 iterations. Failed code attempts
are persisted; five failures lock the link for 15 minutes. Website sessions
expire after eight hours and are checked against the current code generation
on every form/submission operation. The origin also applies a bounded client
request throttle. First-use setup is serialized with a database row lock.

Raw link tokens are not stored in the database. They are reconstructed for an
authorized copy operation using the private website connection key and a
random link seed; only the bearer-token hash is used for public lookup.
Changing the website connection key requires rotating organization links.
Explicit link rotation/revocation invalidates previous access without deleting
submissions. Rotation and revocation preserve the existing access-code hash;
link-management permission cannot be used to bypass the separate code-reset
permission. A code reset does not reactivate a revoked link. Template deletion
revokes its submission link and preserves data.
Turning record saving off blocks website access and queue operations.

## Studio review and printing

**Submission queue** lists the selected template's entries, newest first, in
pages of 100. Ordinary processing staff see active entries only; browsing
completed/rejected submissions and historical print records requires ADMIN.
**Review / claim** gives one staff member a 30-minute review lease.
Other registers cannot claim that entry until it is released or the lease
expires. Studio loads details and original photos into the current template;
it never loads an archived design. Missing/new bindings require review.
Replacing a photo while reviewing retains the submitted text values.

Staff can correct details and crop photos, **Release current review**, or
**Reject current submission** with a reason. Rejection retains its audit data.
New person, sign-out, or switching templates releases an abandoned review on
a best-effort basis; the lease handles disconnected clients.

After the print dialog is accepted, the server checks the current template
revision/schema and claim, saves a durable print record, and moves the entry
to `PRINTING` in one database transaction. A failure prevents print submission.
The printer callback changes it to `JOB_SENT` only after the driver accepts
the job (including both requested sides). This is not proof of physical output.

Canceled/failed/uncertain jobs may remain `PRINTING`. **Admin: retry selected**
requires an active administrator and explicit duplicate-print acknowledgement;
it returns the entry to pending. Ordinary staff cannot automatically retry a
job that might have printed. Queued originals and previous print records are
retained. A new attempt creates a separate print record.

## Storage and verification

Links, session hashes and submissions are private PostgreSQL tables, scoped
by storage store and stable template UUID. Submissions include original upload
bytes, field values and photo framing. They are excluded from cloud/reference
mirroring and the temporary photo queue's cleanup. Schema changes cover local
base SQL, ordered migrations, provisioning's schema contract and query indexes.
Only permission definitions follow the applicable cloud/local catalogue paths.
Public requests pass through the pinned HTTPS storefront origin; origin keys
never reach the browser. Redirects, cross-origin POSTs and oversized bodies
are rejected. No queue/code/photo payload is written to security audit messages.

Validation commands from the repository root:

```powershell
mvn -q -f SmartStock/pom.xml test
mvn -q -f SmartStudio/pom.xml test
node --test SmartStock/cloudflare/smartstock-storefront/test/*.test.mjs
git diff --check
```

The optional `StudioSubmissionQueueDatabaseTest` uses only a disposable
`template_submission_test` database at localhost port 55467, enabled by
`-Dsmartstock.test.submissionDatabaseUrl=jdbc:postgresql://127.0.0.1:55467/template_submission_test`.
It resets that explicitly named database schema; never point it at a store.
Tests cover concurrent first use/claiming, reset permission and session
invalidation, lockouts, persistence/idempotency, schema/revision changes,
queue isolation/pagination, rejection and print-record transitions.

The local browser fixture checks layout, custom inputs, two photo uploads,
receipt display and blank next-person entry. It does not validate production
gateway deployment, mobile camera behavior, live LAN permissions, installed
upgrades, database rollout or physical printing. Those require separate checks.

Final source validation on 19 October 2026: 1,134 SmartStock tests (81 skipped,
zero failures/errors), including nine tests against the disposable PostgreSQL
cluster; 166 SmartStudio tests; 59 gateway tests; repository security and
whitespace checks passed. The temporary preview and database services were
stopped. Windows releases and the storefront gateway are now published;
hosted downloads and metadata were verified. No running store installation
or live store schema was upgraded. Production page/isolation smoke checks
passed; live mobile, LAN, database rollout and printer acceptance remain.
