-- Local-only security boundary for the SmartStock LAN service.
-- Registers never receive grants on these tables; only the server database role
-- and explicitly configured backup/administration roles may access them.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS api_credential_hash TEXT;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS api_previous_credential_hash TEXT;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS api_credential_issued_at TIMESTAMPTZ;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS api_credential_expires_at TIMESTAMPTZ;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS api_previous_expires_at TIMESTAMPTZ;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS api_credential_last_used_at TIMESTAMPTZ;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS api_server_fingerprint TEXT;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS api_pairing_challenge_hash TEXT;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS api_pairing_challenge_expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS devices_api_credential_hash_idx
ON public.devices(api_credential_hash)
WHERE api_credential_hash IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.lan_api_sessions (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_hash TEXT NOT NULL UNIQUE,
    device_id UUID NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    location_id INTEGER NOT NULL REFERENCES public.locations(location_id) ON DELETE CASCADE,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    auth_source TEXT NOT NULL,
    CHECK (auth_source IN ('SUPABASE', 'SUPABASE_PASSWORD', 'LOCAL_CACHE',
                           'LOCAL_PASSWORD_CACHE', 'BADGE_PIN', 'EMPLOYEE_PIN'))
);

ALTER TABLE public.lan_api_sessions
    DROP CONSTRAINT IF EXISTS lan_api_sessions_auth_source_check;
ALTER TABLE public.lan_api_sessions
    ADD CONSTRAINT lan_api_sessions_auth_source_check
    CHECK (auth_source IN ('SUPABASE', 'SUPABASE_PASSWORD', 'LOCAL_CACHE',
                           'LOCAL_PASSWORD_CACHE', 'BADGE_PIN', 'EMPLOYEE_PIN'));

CREATE INDEX IF NOT EXISTS lan_api_sessions_device_active_idx
ON public.lan_api_sessions(device_id, expires_at)
WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS lan_api_sessions_user_active_idx
ON public.lan_api_sessions(user_id, expires_at)
WHERE revoked_at IS NULL;

CREATE TABLE IF NOT EXISTS public.lan_api_idempotency (
    device_id UUID NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    idempotency_key TEXT NOT NULL,
    operation_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    response_status INTEGER,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (device_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS lan_api_idempotency_created_idx
ON public.lan_api_idempotency(created_at);

CREATE TABLE IF NOT EXISTS public.lan_api_approvals (
    approval_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_hash TEXT NOT NULL UNIQUE,
    device_id UUID NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    requester_user_id INTEGER NOT NULL REFERENCES public.users(user_id),
    approver_user_id INTEGER NOT NULL REFERENCES public.users(user_id),
    location_id INTEGER NOT NULL REFERENCES public.locations(location_id),
    permission_key TEXT NOT NULL,
    action_key TEXT NOT NULL,
    resource_hash TEXT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS lan_api_approvals_active_idx
ON public.lan_api_approvals(device_id, expires_at)
WHERE consumed_at IS NULL;

CREATE TABLE IF NOT EXISTS public.lan_api_schedule_proposals (
    proposal_id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    location_id INTEGER NOT NULL REFERENCES public.locations(location_id) ON DELETE CASCADE,
    proposal_hash TEXT NOT NULL,
    proposal_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP + INTERVAL '30 minutes',
    consumed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS lan_api_schedule_proposals_active_idx
ON public.lan_api_schedule_proposals(device_id, user_id, location_id, expires_at)
WHERE consumed_at IS NULL;

CREATE TABLE IF NOT EXISTS public.lan_api_request_audit (
    request_id UUID PRIMARY KEY,
    device_id UUID REFERENCES public.devices(device_id) ON DELETE SET NULL,
    user_id INTEGER REFERENCES public.users(user_id) ON DELETE SET NULL,
    location_id INTEGER REFERENCES public.locations(location_id) ON DELETE SET NULL,
    method TEXT NOT NULL,
    route TEXT NOT NULL,
    operation_key TEXT,
    outcome TEXT NOT NULL,
    status_code INTEGER NOT NULL,
    source_address INET,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS lan_api_request_audit_created_idx
ON public.lan_api_request_audit(created_at DESC);

CREATE OR REPLACE FUNCTION public.reject_lan_api_audit_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    RAISE EXCEPTION 'SmartStock LAN API audit events are immutable';
END;
$$;

DROP TRIGGER IF EXISTS lan_api_request_audit_immutable ON public.lan_api_request_audit;
CREATE TRIGGER lan_api_request_audit_immutable
BEFORE UPDATE OR DELETE ON public.lan_api_request_audit
FOR EACH ROW EXECUTE FUNCTION public.reject_lan_api_audit_mutation();

ALTER TABLE public.lan_api_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lan_api_idempotency ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lan_api_approvals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lan_api_schedule_proposals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lan_api_request_audit ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.lan_api_sessions FROM PUBLIC;
REVOKE ALL ON public.lan_api_idempotency FROM PUBLIC;
REVOKE ALL ON public.lan_api_approvals FROM PUBLIC;
REVOKE ALL ON public.lan_api_schedule_proposals FROM PUBLIC;
REVOKE ALL ON public.lan_api_request_audit FROM PUBLIC;

DO $$
DECLARE role_name TEXT;
BEGIN
    FOREACH role_name IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
            EXECUTE format('REVOKE ALL ON public.lan_api_sessions FROM %I', role_name);
            EXECUTE format('REVOKE ALL ON public.lan_api_idempotency FROM %I', role_name);
            EXECUTE format('REVOKE ALL ON public.lan_api_approvals FROM %I', role_name);
            EXECUTE format('REVOKE ALL ON public.lan_api_schedule_proposals FROM %I', role_name);
            EXECUTE format('REVOKE ALL ON public.lan_api_request_audit FROM %I', role_name);
        END IF;
    END LOOP;
END $$;
