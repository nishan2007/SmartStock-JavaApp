-- Device management setup for SmartStock.
-- Run this in Supabase SQL Editor before using the Device Management screen.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS devices (
    device_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    installation_id TEXT NOT NULL UNIQUE,
    device_fingerprint TEXT,
    device_name TEXT,
    hostname TEXT,
    os_name TEXT,
    os_version TEXT,
    os_arch TEXT,
    java_version TEXT,
    app_version TEXT,
    local_username TEXT,
    mac_addresses TEXT,
    first_seen TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_user_id INTEGER REFERENCES users(user_id),
    last_store_id INTEGER REFERENCES locations(location_id),
    is_approved BOOLEAN NOT NULL DEFAULT FALSE,
    allow_persistent_login BOOLEAN NOT NULL DEFAULT FALSE,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    approved_at TIMESTAMPTZ,
    approved_by_user_id INTEGER REFERENCES users(user_id),
    blocked_at TIMESTAMPTZ,
    blocked_by_user_id INTEGER REFERENCES users(user_id),
    status_notes TEXT,
    receipt_device_code TEXT NOT NULL DEFAULT '0001',
    allow_sales BOOLEAN NOT NULL DEFAULT TRUE,
    allow_orders BOOLEAN NOT NULL DEFAULT TRUE,
    session_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE devices
ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;
ALTER TABLE devices
ADD COLUMN IF NOT EXISTS allow_persistent_login BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE devices
ADD COLUMN IF NOT EXISTS approved_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE devices
ADD COLUMN IF NOT EXISTS blocked_at TIMESTAMPTZ;

ALTER TABLE devices
ADD COLUMN IF NOT EXISTS blocked_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE devices
ADD COLUMN IF NOT EXISTS status_notes TEXT;
ALTER TABLE devices
ADD COLUMN IF NOT EXISTS receipt_device_code TEXT NOT NULL DEFAULT '0001';
ALTER TABLE devices
ADD COLUMN IF NOT EXISTS allow_sales BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE devices
ADD COLUMN IF NOT EXISTS allow_orders BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE devices
ADD COLUMN IF NOT EXISTS session_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE devices
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE devices ADD COLUMN IF NOT EXISTS pairing_public_key TEXT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS credential_status TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE devices ADD COLUMN IF NOT EXISTS credential_issued_at TIMESTAMPTZ;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS credential_claimed_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'devices_credential_status_check'
          AND conrelid = 'public.devices'::regclass
    ) THEN
        ALTER TABLE public.devices ADD CONSTRAINT devices_credential_status_check
            CHECK (credential_status IN ('PENDING', 'ISSUED', 'CLAIMED', 'ROTATION_PENDING', 'REVOKED'));
    END IF;
END $$;

DROP INDEX IF EXISTS devices_credential_role_name_idx;
ALTER TABLE devices DROP COLUMN IF EXISTS credential_role_name;
ALTER TABLE devices DROP COLUMN IF EXISTS previous_credential_role_name;
ALTER TABLE devices DROP COLUMN IF EXISTS credential_envelope;

CREATE TABLE IF NOT EXISTS security_audit_events (
    event_id BIGSERIAL PRIMARY KEY,
    event_type TEXT NOT NULL,
    device_id UUID REFERENCES devices(device_id) ON DELETE SET NULL,
    actor_user_id INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    details TEXT,
    source_address INET DEFAULT inet_client_addr(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS security_audit_events_created_idx
ON security_audit_events(created_at DESC);

CREATE INDEX IF NOT EXISTS security_audit_events_device_idx
ON security_audit_events(device_id, created_at DESC);

CREATE TABLE IF NOT EXISTS login_security_state (
    identifier_hash TEXT PRIMARY KEY,
    failed_count INTEGER NOT NULL DEFAULT 0,
    window_started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMPTZ,
    last_failed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE login_security_state ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE login_security_state FROM PUBLIC;

CREATE OR REPLACE FUNCTION reject_security_audit_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    RAISE EXCEPTION 'SmartStock security audit events are immutable';
END;
$$;

DROP TRIGGER IF EXISTS security_audit_events_immutable ON security_audit_events;
CREATE TRIGGER security_audit_events_immutable
BEFORE UPDATE OR DELETE ON security_audit_events
FOR EACH ROW EXECUTE FUNCTION reject_security_audit_mutation();

ALTER TABLE security_audit_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE security_audit_events FROM PUBLIC;
REVOKE ALL ON SEQUENCE security_audit_events_event_id_seq FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE security_audit_events FROM anon;
        REVOKE ALL ON SEQUENCE security_audit_events_event_id_seq FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE security_audit_events FROM authenticated;
        REVOKE ALL ON SEQUENCE security_audit_events_event_id_seq FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT SELECT, INSERT ON TABLE security_audit_events TO service_role;
        GRANT USAGE, SELECT ON SEQUENCE security_audit_events_event_id_seq TO service_role;
        DROP POLICY IF EXISTS security_audit_events_service_role_access ON security_audit_events;
        CREATE POLICY security_audit_events_service_role_access ON security_audit_events
            FOR ALL TO service_role USING (true) WITH CHECK (true);
    END IF;
    IF to_regprocedure('auth.uid()') IS NULL
       AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        DROP POLICY IF EXISTS security_audit_events_local_app_access ON security_audit_events;
        CREATE POLICY security_audit_events_local_app_access ON security_audit_events
            FOR SELECT TO authenticated USING (true);
        DROP POLICY IF EXISTS security_audit_events_local_app_insert ON security_audit_events;
        CREATE POLICY security_audit_events_local_app_insert ON security_audit_events
            FOR INSERT TO authenticated WITH CHECK (true);
        DROP POLICY IF EXISTS login_security_state_local_app_access ON login_security_state;
        CREATE POLICY login_security_state_local_app_access ON login_security_state
            FOR ALL TO authenticated USING (true) WITH CHECK (true);
    END IF;
END $$;

ALTER TABLE devices
ALTER COLUMN is_approved SET DEFAULT FALSE;

ALTER TABLE devices
ALTER COLUMN is_blocked SET DEFAULT FALSE;

CREATE OR REPLACE FUNCTION set_devices_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS devices_set_updated_at ON devices;
CREATE TRIGGER devices_set_updated_at
BEFORE INSERT OR UPDATE ON devices
FOR EACH ROW
EXECUTE FUNCTION set_devices_updated_at();

CREATE INDEX IF NOT EXISTS devices_last_seen_idx
ON devices(last_seen DESC);

CREATE INDEX IF NOT EXISTS devices_installation_id_idx
ON devices(installation_id);

CREATE INDEX IF NOT EXISTS devices_updated_at_idx
ON devices(updated_at DESC);

CREATE TABLE IF NOT EXISTS device_sessions (
    session_id BIGSERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    user_id INTEGER REFERENCES users(user_id),
    store_id INTEGER REFERENCES locations(location_id),
    login_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    logout_time TIMESTAMPTZ,
    session_status TEXT NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX IF NOT EXISTS device_sessions_device_login_idx
ON device_sessions(device_id, login_time DESC);

CREATE INDEX IF NOT EXISTS device_sessions_active_idx
ON device_sessions(device_id, session_status, logout_time);

CREATE INDEX IF NOT EXISTS device_sessions_login_time_idx
ON device_sessions(login_time DESC);

DELETE FROM device_sessions
WHERE login_time < CURRENT_TIMESTAMP - INTERVAL '30 days';

UPDATE devices d
SET session_count = COALESCE(session_totals.session_count, 0)
FROM (
    SELECT d2.device_id, COUNT(ds.session_id)::BIGINT AS session_count
    FROM devices d2
    LEFT JOIN device_sessions ds ON ds.device_id = d2.device_id
    GROUP BY d2.device_id
) session_totals
WHERE session_totals.device_id = d.device_id;

DO $outer$
BEGIN
    IF to_regprocedure('public.refresh_device_session_count()') IS NULL THEN
        EXECUTE $function$
            CREATE FUNCTION public.refresh_device_session_count()
            RETURNS TRIGGER
            LANGUAGE plpgsql
            AS $body$
            BEGIN
                IF TG_OP IN ('INSERT', 'UPDATE') THEN
                    UPDATE public.devices
                    SET session_count = (
                        SELECT COUNT(*)::BIGINT
                        FROM public.device_sessions
                        WHERE device_id = NEW.device_id
                    )
                    WHERE device_id = NEW.device_id;
                END IF;

                IF TG_OP IN ('DELETE', 'UPDATE')
                   AND (TG_OP = 'DELETE' OR OLD.device_id IS DISTINCT FROM NEW.device_id) THEN
                    UPDATE public.devices
                    SET session_count = (
                        SELECT COUNT(*)::BIGINT
                        FROM public.device_sessions
                        WHERE device_id = OLD.device_id
                    )
                    WHERE device_id = OLD.device_id;
                END IF;

                RETURN COALESCE(NEW, OLD);
            END;
            $body$
        $function$;
    END IF;
END;
$outer$;

DROP TRIGGER IF EXISTS device_sessions_refresh_count ON device_sessions;
CREATE TRIGGER device_sessions_refresh_count
AFTER INSERT OR UPDATE OR DELETE ON device_sessions
FOR EACH ROW
EXECUTE FUNCTION refresh_device_session_count();

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS permission_group TEXT;

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS permission_subgroup TEXT;

INSERT INTO permissions (permission_key, permission_name, permission_group, permission_subgroup)
VALUES ('DEVICE_MANAGEMENT', 'Device Management', 'Administration', 'Devices')
ON CONFLICT (permission_key) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    permission_group = EXCLUDED.permission_group,
    permission_subgroup = EXCLUDED.permission_subgroup;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'DEVICE_MANAGEMENT'
WHERE UPPER(r.role_name) = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
