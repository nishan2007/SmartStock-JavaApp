CREATE TABLE IF NOT EXISTS public.custom_order_held_carts (
 hold_id text PRIMARY KEY,
 location_id integer NOT NULL REFERENCES public.locations(location_id),
 user_id integer NOT NULL REFERENCES public.users(user_id),
 device_id text NOT NULL,
 hold_name text NOT NULL,
 payload jsonb NOT NULL,
 status text NOT NULL DEFAULT 'STAGING' CHECK(status IN ('STAGING','HELD','RESUMED')),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 resumed_at timestamptz
);
CREATE INDEX IF NOT EXISTS custom_order_held_carts_available_idx
 ON public.custom_order_held_carts(location_id,created_at DESC) WHERE status='HELD';
CREATE TABLE IF NOT EXISTS public.custom_order_held_files (
 hold_id text NOT NULL REFERENCES public.custom_order_held_carts(hold_id) ON DELETE CASCADE,
 file_id text NOT NULL,
 chunk_no integer NOT NULL CHECK(chunk_no>=0),
 content bytea NOT NULL CHECK(octet_length(content)<=524288),
 PRIMARY KEY(hold_id,file_id,chunk_no)
);