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

CREATE OR REPLACE FUNCTION refresh_device_session_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        UPDATE devices
        SET session_count = (
            SELECT COUNT(*)::BIGINT
            FROM device_sessions
            WHERE device_id = NEW.device_id
        )
        WHERE device_id = NEW.device_id;
    END IF;

    IF TG_OP IN ('DELETE', 'UPDATE')
       AND (TG_OP = 'DELETE' OR OLD.device_id IS DISTINCT FROM NEW.device_id) THEN
        UPDATE devices
        SET session_count = (
            SELECT COUNT(*)::BIGINT
            FROM device_sessions
            WHERE device_id = OLD.device_id
        )
        WHERE device_id = OLD.device_id;
    END IF;

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS device_sessions_refresh_count ON device_sessions;
CREATE TRIGGER device_sessions_refresh_count
AFTER INSERT OR UPDATE OR DELETE ON device_sessions
FOR EACH ROW
EXECUTE FUNCTION refresh_device_session_count();

INSERT INTO permissions (permission_key, permission_name)
SELECT 'DEVICE_MANAGEMENT', 'Device Management'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'DEVICE_MANAGEMENT'
);

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
