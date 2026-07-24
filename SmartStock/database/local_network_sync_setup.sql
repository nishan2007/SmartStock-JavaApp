CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS sync_outbox (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type TEXT NOT NULL,
    location_id INTEGER,
    device_id TEXT,
    user_id INTEGER,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    synced_at TIMESTAMPTZ,
    origin_event_id UUID,
    origin_location_id INTEGER,
    origin_device_id TEXT,
    origin_created_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS sync_outbox_status_created_idx
    ON sync_outbox(status, created_at);

CREATE TABLE IF NOT EXISTS sync_applied_events (
    origin_event_id UUID PRIMARY KEY,
    event_type TEXT NOT NULL,
    origin_location_id INTEGER,
    origin_device_id TEXT,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cloud_reference TEXT
);

CREATE TABLE IF NOT EXISTS sync_id_map (
    id_map_id BIGSERIAL PRIMARY KEY,
    origin_event_id UUID,
    table_name TEXT NOT NULL,
    local_id TEXT NOT NULL,
    cloud_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(table_name, local_id)
);

CREATE TABLE IF NOT EXISTS sync_tombstones (
    tombstone_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    table_name TEXT NOT NULL,
    key_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    origin_device_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(table_name, key_data)
);

CREATE INDEX IF NOT EXISTS sync_tombstones_deleted_idx
    ON sync_tombstones(deleted_at DESC);

CREATE INDEX IF NOT EXISTS sync_tombstones_table_idx
    ON sync_tombstones(table_name);

CREATE TABLE IF NOT EXISTS sync_conflicts (
    conflict_id BIGSERIAL PRIMARY KEY,
    origin_event_id UUID,
    event_type TEXT,
    table_name TEXT,
    local_id TEXT,
    conflict_type TEXT NOT NULL,
    local_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    cloud_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL DEFAULT 'OPEN',
    resolution_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,
    resolved_by_user_id INTEGER,
    resolved_by_name TEXT
);

CREATE INDEX IF NOT EXISTS sync_conflicts_status_created_idx
    ON sync_conflicts(status, created_at);

CREATE TABLE IF NOT EXISTS sync_audit_log (
    sync_audit_id BIGSERIAL PRIMARY KEY,
    action_type TEXT NOT NULL,
    table_name TEXT,
    local_id_before TEXT,
    local_id_after TEXT,
    cloud_id TEXT,
    match_key TEXT,
    status TEXT NOT NULL DEFAULT 'INFO',
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS sync_audit_log_created_idx
    ON sync_audit_log(created_at DESC);

CREATE INDEX IF NOT EXISTS sync_audit_log_table_created_idx
    ON sync_audit_log(table_name, created_at DESC);

DO $$
DECLARE
    target_table TEXT;
    target_sequence TEXT;
    has_anon BOOLEAN := EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon');
    has_authenticated BOOLEAN := EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated');
    has_service_role BOOLEAN := EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role');
BEGIN
    FOREACH target_table IN ARRAY ARRAY[
        'sync_outbox',
        'sync_applied_events',
        'sync_id_map',
        'sync_tombstones',
        'sync_conflicts',
        'sync_audit_log'
    ]
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', target_table);
        EXECUTE format('REVOKE ALL ON TABLE public.%I FROM PUBLIC', target_table);
        IF has_anon THEN
            EXECUTE format('REVOKE ALL ON TABLE public.%I FROM anon', target_table);
        END IF;
        IF has_authenticated THEN
            EXECUTE format('REVOKE ALL ON TABLE public.%I FROM authenticated', target_table);
            EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', target_table || '_authenticated_all', target_table);
            EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', target_table || '_anon_all', target_table);
        END IF;
        IF has_service_role THEN
            EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', target_table || '_service_role_all', target_table);
            EXECUTE format(
                'CREATE POLICY %I ON public.%I FOR ALL TO service_role USING (true) WITH CHECK (true)',
                target_table || '_service_role_all',
                target_table
            );
            EXECUTE format('GRANT ALL ON TABLE public.%I TO service_role', target_table);
        END IF;

        FOR target_sequence IN
            SELECT pg_get_serial_sequence('public.' || c.table_name, c.column_name)
            FROM information_schema.columns c
            WHERE c.table_schema = 'public'
              AND c.table_name = target_table
              AND c.column_default LIKE 'nextval(%'
        LOOP
            IF target_sequence IS NOT NULL THEN
                EXECUTE format('REVOKE ALL ON SEQUENCE %s FROM PUBLIC', target_sequence);
                IF has_anon THEN
                    EXECUTE format('REVOKE ALL ON SEQUENCE %s FROM anon', target_sequence);
                END IF;
                IF has_authenticated THEN
                    EXECUTE format('REVOKE ALL ON SEQUENCE %s FROM authenticated', target_sequence);
                END IF;
                IF has_service_role THEN
                    EXECUTE format('GRANT ALL ON SEQUENCE %s TO service_role', target_sequence);
                END IF;
            END IF;
        END LOOP;
    END LOOP;
END $$;

CREATE TABLE IF NOT EXISTS local_auth_cache (
    user_id INTEGER PRIMARY KEY,
    username TEXT NOT NULL,
    full_name TEXT,
    nickname TEXT,
    email TEXT,
    badge_id TEXT,
    role_name TEXT,
    location_id INTEGER,
    location_name TEXT,
    location_timezone TEXT,
    pin_salt TEXT,
    pin_hash TEXT,
    password_salt TEXT,
    password_hash TEXT,
    pin_cached_at TIMESTAMPTZ,
    employee_pin_salt TEXT,
    employee_pin_hash TEXT,
    employee_pin_cached_at TIMESTAMPTZ,
    password_cached_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    cached_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE local_auth_cache
    ALTER COLUMN pin_salt DROP NOT NULL,
    ALTER COLUMN pin_hash DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS password_salt TEXT,
    ADD COLUMN IF NOT EXISTS password_hash TEXT,
    ADD COLUMN IF NOT EXISTS pin_cached_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS employee_pin_salt TEXT,
    ADD COLUMN IF NOT EXISTS employee_pin_hash TEXT,
    ADD COLUMN IF NOT EXISTS employee_pin_cached_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS password_cached_at TIMESTAMPTZ;

UPDATE local_auth_cache
SET employee_pin_salt = COALESCE(employee_pin_salt, pin_salt),
    employee_pin_hash = COALESCE(employee_pin_hash, pin_hash),
    employee_pin_cached_at = COALESCE(employee_pin_cached_at, pin_cached_at, cached_at)
WHERE employee_pin_hash IS NULL
  AND pin_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS local_auth_cache_username_idx
    ON local_auth_cache(LOWER(username));

CREATE INDEX IF NOT EXISTS local_auth_cache_email_idx
    ON local_auth_cache(LOWER(email));

CREATE INDEX IF NOT EXISTS local_auth_cache_badge_idx
    ON local_auth_cache(LOWER(badge_id));

CREATE INDEX IF NOT EXISTS local_auth_cache_badge_normalized_idx
    ON local_auth_cache(UPPER(REGEXP_REPLACE(COALESCE(badge_id, ''), '[^a-zA-Z0-9]', '', 'g')));
