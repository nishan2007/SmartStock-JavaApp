CREATE TABLE IF NOT EXISTS public.mobile_item_web_runtime (
    runtime_id smallint PRIMARY KEY DEFAULT 1 CHECK (runtime_id = 1),
    enabled boolean NOT NULL DEFAULT false,
    generation uuid NOT NULL DEFAULT gen_random_uuid(),
    changed_by_user_id integer REFERENCES public.users(user_id) ON DELETE SET NULL,
    changed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO public.mobile_item_web_runtime(runtime_id, enabled)
VALUES (1, false) ON CONFLICT (runtime_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.mobile_item_web_activations (
    activation_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    generation uuid NOT NULL,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    revoked_at timestamptz,
    created_by_user_id integer REFERENCES public.users(user_id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.mobile_item_web_browsers (
    browser_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    generation uuid NOT NULL,
    credential_hash text NOT NULL UNIQUE,
    activated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at timestamptz
);

CREATE TABLE IF NOT EXISTS public.mobile_item_web_sessions (
    session_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    browser_id uuid NOT NULL REFERENCES public.mobile_item_web_browsers(browser_id) ON DELETE CASCADE,
    session_hash text NOT NULL UNIQUE,
    csrf_hash text NOT NULL,
    user_id integer NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    location_id integer NOT NULL REFERENCES public.locations(location_id) ON DELETE CASCADE,
    auth_source text NOT NULL,
    expires_at timestamptz NOT NULL,
    absolute_expires_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.mobile_item_web_idempotency (
    browser_id uuid NOT NULL REFERENCES public.mobile_item_web_browsers(browser_id) ON DELETE CASCADE,
    idempotency_key text NOT NULL,
    operation_key text NOT NULL,
    request_hash text NOT NULL,
    response_json jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (browser_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS mobile_item_web_activation_active_idx
    ON public.mobile_item_web_activations(generation, expires_at)
    WHERE used_at IS NULL AND revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS mobile_item_web_browser_active_idx
    ON public.mobile_item_web_browsers(generation, last_seen_at)
    WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS mobile_item_web_session_active_idx
    ON public.mobile_item_web_sessions(browser_id, expires_at)
    WHERE revoked_at IS NULL;

ALTER TABLE public.mobile_item_web_runtime ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobile_item_web_activations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobile_item_web_browsers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobile_item_web_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobile_item_web_idempotency ENABLE ROW LEVEL SECURITY;
