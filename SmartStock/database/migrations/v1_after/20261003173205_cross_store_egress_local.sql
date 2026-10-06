CREATE TABLE IF NOT EXISTS public.sync_cross_store_refresh (
    source_location_id integer PRIMARY KEY,
    next_refresh_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    successful_refresh_at timestamptz,
    source_completed_at timestamptz,
    generation_id uuid,
    last_error text
);
CREATE TABLE IF NOT EXISTS public.sync_cross_store_table_state (
    source_location_id integer NOT NULL,
    table_name text NOT NULL,
    fingerprint text NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    row_count bigint NOT NULL CHECK (row_count>=0),
    PRIMARY KEY(source_location_id,table_name)
);
CREATE TABLE IF NOT EXISTS public.sync_cross_store_source_rows (
    source_location_id integer NOT NULL,
    table_name text NOT NULL,
    row_key jsonb NOT NULL,
    row_hash text NOT NULL,
    row_data jsonb NOT NULL,
    PRIMARY KEY(source_location_id,table_name,row_key)
);
ALTER TABLE public.sync_transfer_metrics ADD COLUMN IF NOT EXISTS source_location_id integer;
CREATE INDEX IF NOT EXISTS sync_transfer_metrics_created_idx ON public.sync_transfer_metrics(created_at);
-- An exact billing start is configured separately; never assume calendar months.
CREATE TABLE IF NOT EXISTS public.sync_transfer_settings (
    settings_id integer PRIMARY KEY CHECK(settings_id=1),
    billing_period_start timestamptz
);
INSERT INTO public.sync_transfer_settings(settings_id) VALUES(1) ON CONFLICT DO NOTHING;
-- Local PostgreSQL is not a public API. No Supabase client role can read these caches.
REVOKE ALL ON public.sync_cross_store_refresh,public.sync_cross_store_table_state,
    public.sync_cross_store_source_rows,public.sync_transfer_settings FROM PUBLIC;
ALTER TABLE public.sync_cross_store_refresh ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_cross_store_table_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_cross_store_source_rows ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_transfer_settings ENABLE ROW LEVEL SECURITY;
