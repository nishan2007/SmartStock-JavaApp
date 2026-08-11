CREATE TABLE IF NOT EXISTS register_transfers (
    transfer_id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    device_id uuid NOT NULL REFERENCES devices(device_id),
    installation_id text NOT NULL,
    source_location_id integer REFERENCES locations(location_id),
    destination_location_id integer NOT NULL REFERENCES locations(location_id),
    status text DEFAULT 'PREPARED' NOT NULL,
    emergency boolean DEFAULT false NOT NULL,
    reason text,
    initiated_by_user_id integer REFERENCES users(user_id),
    completed_by_user_id integer REFERENCES users(user_id),
    prepared_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT register_transfers_status_check
        CHECK (status IN ('PREPARED', 'COMPLETED', 'CANCELLED', 'EXPIRED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS register_transfers_one_prepared_device_idx
    ON register_transfers(device_id) WHERE status = 'PREPARED';
CREATE INDEX IF NOT EXISTS register_transfers_destination_idx
    ON register_transfers(destination_location_id, status, expires_at);
CREATE INDEX IF NOT EXISTS register_transfers_installation_idx
    ON register_transfers(installation_id, status, expires_at);
