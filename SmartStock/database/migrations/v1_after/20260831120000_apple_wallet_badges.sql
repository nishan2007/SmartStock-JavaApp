CREATE TABLE IF NOT EXISTS public.employee_wallet_credentials (
    wallet_credential_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id integer NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    credential_hash text NOT NULL UNIQUE,
    serial_number text NOT NULL UNIQUE,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVOKED')),
    issued_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    issued_by_user_id integer REFERENCES public.users(user_id),
    revoked_at timestamp with time zone,
    revoked_by_user_id integer REFERENCES public.users(user_id),
    last_used_at timestamp with time zone,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((status='ACTIVE' AND revoked_at IS NULL) OR status='REVOKED')
);

CREATE UNIQUE INDEX IF NOT EXISTS employee_wallet_one_active_idx
ON public.employee_wallet_credentials(user_id) WHERE status='ACTIVE';
CREATE INDEX IF NOT EXISTS employee_wallet_user_idx
ON public.employee_wallet_credentials(user_id, issued_at DESC);

CREATE TABLE IF NOT EXISTS public.employee_wallet_enrollments (
    enrollment_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id integer NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id integer NOT NULL REFERENCES public.users(user_id),
    CHECK (expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS employee_wallet_enrollment_user_idx
ON public.employee_wallet_enrollments(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS employee_wallet_enrollment_expiry_idx
ON public.employee_wallet_enrollments(expires_at) WHERE consumed_at IS NULL;

ALTER TABLE public.employee_wallet_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.employee_wallet_enrollments ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.employee_wallet_credentials,public.employee_wallet_enrollments FROM PUBLIC;
