# Post-v1 migrations

Add future schema changes here as immutable, timestamped SQL migrations and append
them to the applicable ordered manifest. Never edit a migration after it has been
applied. The canonical v1 baselines remain under `database/v1/local` and
`database/v1/cloud`.

Tables that are reachable only through service-role RPCs intentionally have RLS
enabled without client policies. Supabase may report these as informational
`rls_enabled_no_policy` notices; the explicit grants and function revocations are
the access contract.
