# SmartStock schema parity contract

The application, local PostgreSQL database, and hosted Supabase database share
one business-column contract. Shared columns must agree on data type semantics,
nullability, default behavior, and business constraints. PostgreSQL-equivalent
representations are accepted (for example, `text` versus unconstrained
`varchar`, `CURRENT_TIMESTAMP` versus `now()`, and identity columns versus
sequence-backed integer columns).

Forward fixes belong in `database/migrations/`. Fresh-install definitions and
runtime repair code must be updated in the same change so a new local database,
an upgraded local database, and Supabase converge on the same contract.

## Intentional environment boundaries

Local-only tables hold workstation authentication state, LAN API credentials,
idempotency records, approvals, and local security audit data:

- `lan_api_approvals`
- `lan_api_idempotency`
- `lan_api_request_audit`
- `lan_api_schedule_proposals`
- `lan_api_sessions`
- `local_auth_cache`
- `login_security_state`
- `security_audit_events`

The following `devices` columns are also local-only because credential hashes,
pairing challenges, and server fingerprints must not be copied to Supabase:

- `api_credential_hash`, `api_previous_credential_hash`
- `api_credential_issued_at`, `api_credential_expires_at`
- `api_previous_expires_at`, `api_credential_last_used_at`
- `api_server_fingerprint`
- `api_pairing_challenge_hash`, `api_pairing_challenge_expires_at`

Supabase retains hosted-only RLS policies, RPC helpers, grants, storage policy,
and compatibility columns used by deployed hosted workflows. These include the
legacy receipt-visibility flags, hosted cash-drawer return metadata, device
notes/location metadata, mobile-permission display ordering, surrogate IDs on
`inventory` and `user_locations`, and legacy `users.pay_period_type`.
Current payroll period behavior is defined by employee payroll settings and
payroll payment records, not that legacy user column.

The local maintenance views additionally project `location_name`; Supabase
also exposes the hosted-only `vw_inventory_details` view.

## Security boundary

Supabase owns the hosted RLS, grants, authenticated RPC surface, and storage
policies. Local PostgreSQL owns LAN secrets and server audit state. Public
pairing lifecycle fields are shared, but private credential material is not.
All hosted non-extension functions in the public schema use the trusted
`pg_catalog, public` search path after migrations are applied. Local functions
owned by the PostgreSQL service account can only be altered by that owner.
