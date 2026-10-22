# Phone queue upload bridge fix — 2026-10-18

Phone pairing succeeded but Add to queue returned "Cannot reach the store queue".
The Worker upload bridge passed `redirect: 'error'` to fetch. Cloudflare Workers
does not support that mode and throws before contacting the origin. Node's fetch
mock accepted the setting, so the original routing test did not expose it.

Changed the bridge to `redirect: 'manual'`. The existing response allowlist rejects
all 3xx statuses, so photos and origin credentials are not forwarded to redirect
destinations. Added regression coverage for rejected redirects and checked the
supported mode in the routing test. All 52 Worker tests and the scoped whitespace
check pass.

Deployed with the existing protected production config, preserving store origins,
photo hostname, and browse-only shopping mode. Worker version:
`8992e4e8-2013-4dd6-8659-8aa45020213b`.

Before deployment, a synthetic invalid-credential upload returned the reproduced
503 connection error. After deployment, the same diagnostic on both photo and
main website hosts returned the store server's 403 credential rejection. This
confirms the upload bridge now reaches the store server. No real photo, phone
pairing, application installation, or store service was changed for that test.
The user's valid paired-phone upload should be retried; that real-phone result
has not been independently observed.

Cloudflare runtime contract:
https://developers.cloudflare.com/workers/runtime-apis/request/#parameters
