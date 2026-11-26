-- Browser retries are store-owned financial records; no tokens or credentials are stored.
CREATE TABLE IF NOT EXISTS public.phone_minutes_web_requests (
 browser_id uuid NOT NULL,
 user_id integer NOT NULL REFERENCES public.users(user_id),
 request_key uuid NOT NULL,
 location_id integer NOT NULL REFERENCES public.locations(location_id),
 operation text NOT NULL CHECK(operation IN ('create','reverse')),
 request_hash text NOT NULL,
 response jsonb,
 created_at timestamptz NOT NULL DEFAULT now(),
 sync_uuid uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,
 PRIMARY KEY(browser_id,user_id,request_key)
);
CREATE INDEX IF NOT EXISTS phone_minutes_web_requests_location_idx ON public.phone_minutes_web_requests(location_id,created_at);
