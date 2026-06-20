CREATE TABLE IF NOT EXISTS change_basket_updates (
    change_basket_update_id BIGSERIAL PRIMARY KEY,
    location_id INTEGER NOT NULL REFERENCES locations(location_id),
    store_name TEXT,
    target_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    counted_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    variance NUMERIC(12,2) NOT NULL DEFAULT 0,
    denomination_counts JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_user_id INTEGER REFERENCES users(user_id),
    updated_by_name TEXT,
    device_id UUID REFERENCES devices(device_id),
    device_name TEXT,
    notes TEXT
);

ALTER TABLE change_basket_updates ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS change_basket_updates_service_role_all ON change_basket_updates;
CREATE POLICY change_basket_updates_service_role_all
ON change_basket_updates
FOR ALL
TO service_role
USING (TRUE)
WITH CHECK (TRUE);

DROP POLICY IF EXISTS change_basket_updates_authenticated_all ON change_basket_updates;
CREATE POLICY change_basket_updates_authenticated_all
ON change_basket_updates
FOR ALL
TO authenticated
USING (TRUE)
WITH CHECK (TRUE);

CREATE INDEX IF NOT EXISTS change_basket_updates_location_updated_idx
ON change_basket_updates(location_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS change_basket_updates_updated_by_user_idx
ON change_basket_updates(updated_by_user_id)
WHERE updated_by_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS change_basket_updates_device_idx
ON change_basket_updates(device_id)
WHERE device_id IS NOT NULL;
