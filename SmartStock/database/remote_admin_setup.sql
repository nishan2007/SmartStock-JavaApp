-- Remote Admin gateway state. Run on local server schemas and the hosted cloud database.
ALTER TABLE devices ADD COLUMN IF NOT EXISTS access_mode TEXT NOT NULL DEFAULT 'CLIENT';

CREATE TABLE IF NOT EXISTS store_sync_status (
    location_id INTEGER PRIMARY KEY REFERENCES locations(location_id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    message TEXT,
    last_success_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS remote_admin_commands (
    command_id UUID PRIMARY KEY,
    location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
    device_id UUID REFERENCES devices(device_id) ON DELETE SET NULL,
    user_id INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    operation TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('APPLIED_CLOUD','PENDING_STORE','APPLIED_STORE','REJECTED','CONFLICT')),
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE store_sync_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE remote_admin_commands ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON store_sync_status, remote_admin_commands FROM PUBLIC, anon, authenticated;
