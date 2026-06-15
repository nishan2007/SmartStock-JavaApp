-- Maintenance management setup for SmartStock.
-- Run this in Supabase SQL Editor before opening the Maintenance screen.

CREATE TABLE IF NOT EXISTS maintenance_machines (
    machine_id BIGSERIAL PRIMARY KEY,
    machine_name TEXT NOT NULL,
    asset_tag TEXT UNIQUE,
    serial_number TEXT,
    manufacturer TEXT,
    model TEXT,
    machine_type TEXT,
    location_id INTEGER REFERENCES locations(location_id),
    location_name TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    purchase_date DATE,
    warranty_expiration_date DATE,
    last_service_date DATE,
    next_service_date DATE,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT maintenance_machines_status_chk
        CHECK (status IN ('ACTIVE', 'NEEDS_SERVICE', 'DOWN', 'RETIRED'))
);

ALTER TABLE maintenance_machines
ADD COLUMN IF NOT EXISTS asset_tag TEXT;

ALTER TABLE maintenance_machines
ADD COLUMN IF NOT EXISTS serial_number TEXT;

ALTER TABLE maintenance_machines
ADD COLUMN IF NOT EXISTS manufacturer TEXT;

ALTER TABLE maintenance_machines
ADD COLUMN IF NOT EXISTS model TEXT;

ALTER TABLE maintenance_machines
ADD COLUMN IF NOT EXISTS machine_type TEXT;

ALTER TABLE maintenance_machines
ADD COLUMN IF NOT EXISTS location_id INTEGER;

ALTER TABLE maintenance_machines
ADD COLUMN IF NOT EXISTS location_name TEXT;

ALTER TABLE maintenance_machines
ADD COLUMN IF NOT EXISTS purchase_date DATE;

ALTER TABLE maintenance_machines
ADD COLUMN IF NOT EXISTS warranty_expiration_date DATE;

ALTER TABLE maintenance_machines
ADD COLUMN IF NOT EXISTS last_service_date DATE;

ALTER TABLE maintenance_machines
ADD COLUMN IF NOT EXISTS next_service_date DATE;

ALTER TABLE maintenance_machines
ADD COLUMN IF NOT EXISTS notes TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS maintenance_machines_asset_tag_uidx
ON maintenance_machines(asset_tag)
WHERE asset_tag IS NOT NULL AND asset_tag <> '';

CREATE INDEX IF NOT EXISTS maintenance_machines_status_idx
ON maintenance_machines(status);

CREATE INDEX IF NOT EXISTS maintenance_machines_next_service_idx
ON maintenance_machines(next_service_date);

CREATE INDEX IF NOT EXISTS maintenance_machines_location_idx
ON maintenance_machines(location_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'maintenance_machines_location_id_fkey'
    ) THEN
        ALTER TABLE maintenance_machines
        ADD CONSTRAINT maintenance_machines_location_id_fkey
        FOREIGN KEY (location_id) REFERENCES locations(location_id);
    END IF;
END;
$$;

UPDATE maintenance_machines mm
SET location_id = l.location_id
FROM locations l
WHERE mm.location_id IS NULL
  AND mm.location_name IS NOT NULL
  AND LOWER(TRIM(mm.location_name)) = LOWER(TRIM(l.name));

CREATE TABLE IF NOT EXISTS maintenance_parts (
    part_id BIGSERIAL PRIMARY KEY,
    part_name TEXT NOT NULL,
    part_number TEXT UNIQUE,
    category TEXT,
    quantity_on_hand NUMERIC(12, 2) NOT NULL DEFAULT 0,
    reorder_point NUMERIC(12, 2) NOT NULL DEFAULT 0,
    reorder_quantity NUMERIC(12, 2) NOT NULL DEFAULT 0,
    unit_cost NUMERIC(12, 2) NOT NULL DEFAULT 0,
    vendor_name TEXT,
    bin_location TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS maintenance_parts_part_number_uidx
ON maintenance_parts(part_number)
WHERE part_number IS NOT NULL AND part_number <> '';

CREATE INDEX IF NOT EXISTS maintenance_parts_reorder_idx
ON maintenance_parts(is_active, quantity_on_hand, reorder_point);

CREATE TABLE IF NOT EXISTS maintenance_machine_parts (
    machine_part_id BIGSERIAL PRIMARY KEY,
    machine_id BIGINT NOT NULL REFERENCES maintenance_machines(machine_id) ON DELETE CASCADE,
    part_id BIGINT NOT NULL REFERENCES maintenance_parts(part_id) ON DELETE RESTRICT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT maintenance_machine_parts_machine_part_uidx UNIQUE (machine_id, part_id)
);

ALTER TABLE maintenance_machine_parts
DROP COLUMN IF EXISTS quantity_required;

CREATE INDEX IF NOT EXISTS maintenance_machine_parts_machine_idx
ON maintenance_machine_parts(machine_id);

CREATE INDEX IF NOT EXISTS maintenance_machine_parts_part_idx
ON maintenance_machine_parts(part_id);

CREATE TABLE IF NOT EXISTS maintenance_logs (
    log_id BIGSERIAL PRIMARY KEY,
    machine_id BIGINT NOT NULL REFERENCES maintenance_machines(machine_id) ON DELETE CASCADE,
    service_date DATE NOT NULL DEFAULT CURRENT_DATE,
    service_type TEXT NOT NULL DEFAULT 'PREVENTIVE',
    technician_name TEXT,
    labor_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    total_cost NUMERIC(12, 2) NOT NULL DEFAULT 0,
    summary TEXT,
    details TEXT,
    parts_used TEXT,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT maintenance_logs_service_type_chk
        CHECK (service_type IN ('PREVENTIVE', 'REPAIR', 'INSPECTION', 'CLEANING', 'CALIBRATION', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS maintenance_logs_machine_date_idx
ON maintenance_logs(machine_id, service_date DESC);

CREATE OR REPLACE FUNCTION refresh_machine_last_service_date()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE maintenance_machines
    SET last_service_date = (
            SELECT MAX(service_date)
            FROM maintenance_logs
            WHERE machine_id = NEW.machine_id
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE machine_id = NEW.machine_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS maintenance_logs_refresh_machine_last_service_date ON maintenance_logs;
CREATE TRIGGER maintenance_logs_refresh_machine_last_service_date
AFTER INSERT OR UPDATE OF machine_id, service_date ON maintenance_logs
FOR EACH ROW
EXECUTE FUNCTION refresh_machine_last_service_date();

CREATE TABLE IF NOT EXISTS maintenance_tickets (
    ticket_id BIGSERIAL PRIMARY KEY,
    machine_id BIGINT REFERENCES maintenance_machines(machine_id) ON DELETE SET NULL,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    opened_by_user_id INTEGER REFERENCES users(user_id),
    priority TEXT NOT NULL DEFAULT 'NORMAL',
    status TEXT NOT NULL DEFAULT 'OPEN',
    assigned_to_name TEXT,
    due_date DATE,
    problem_summary TEXT NOT NULL,
    resolution_summary TEXT,
    notes TEXT,
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT maintenance_tickets_priority_chk
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT maintenance_tickets_status_chk
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING_PARTS', 'RESOLVED', 'CLOSED', 'CANCELED'))
);

CREATE INDEX IF NOT EXISTS maintenance_tickets_status_priority_idx
ON maintenance_tickets(status, priority, opened_at DESC);

CREATE INDEX IF NOT EXISTS maintenance_tickets_machine_idx
ON maintenance_tickets(machine_id, opened_at DESC);

CREATE INDEX IF NOT EXISTS maintenance_tickets_resolved_idx
ON maintenance_tickets(status, resolved_at)
WHERE status = 'RESOLVED';

CREATE OR REPLACE FUNCTION auto_close_resolved_maintenance_tickets()
RETURNS INTEGER AS $$
DECLARE
    closed_count INTEGER;
BEGIN
    UPDATE maintenance_tickets
    SET status = 'CLOSED',
        closed_at = CURRENT_TIMESTAMP,
        updated_at = CURRENT_TIMESTAMP
    WHERE status = 'RESOLVED'
      AND resolved_at IS NOT NULL
      AND resolved_at <= CURRENT_TIMESTAMP - INTERVAL '24 hours'
      AND closed_at IS NULL;

    GET DIAGNOSTICS closed_count = ROW_COUNT;
    RETURN closed_count;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS maintenance_ticket_notes (
    note_id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES maintenance_tickets(ticket_id) ON DELETE CASCADE,
    note_text TEXT NOT NULL,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS maintenance_ticket_notes_ticket_idx
ON maintenance_ticket_notes(ticket_id, created_at DESC);

CREATE TABLE IF NOT EXISTS maintenance_ticket_parts (
    ticket_part_id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES maintenance_tickets(ticket_id) ON DELETE CASCADE,
    part_id BIGINT NOT NULL REFERENCES maintenance_parts(part_id) ON DELETE RESTRICT,
    quantity_used NUMERIC(12, 2) NOT NULL DEFAULT 1,
    unit_cost NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS maintenance_ticket_parts_ticket_idx
ON maintenance_ticket_parts(ticket_id);

CREATE OR REPLACE VIEW maintenance_parts_to_reorder AS
SELECT part_id,
       part_name,
       part_number,
       quantity_on_hand,
       reorder_point,
       reorder_quantity,
       vendor_name,
       bin_location
FROM maintenance_parts
WHERE is_active = TRUE
  AND quantity_on_hand <= reorder_point;

CREATE OR REPLACE VIEW maintenance_machine_part_list AS
SELECT m.machine_id,
       m.machine_name,
       m.asset_tag,
       COALESCE(l.name, m.location_name) AS location_name,
       p.part_id,
       p.part_name,
       p.part_number,
       mp.notes
FROM maintenance_machine_parts mp
JOIN maintenance_machines m ON m.machine_id = mp.machine_id
LEFT JOIN locations l ON l.location_id = m.location_id
JOIN maintenance_parts p ON p.part_id = mp.part_id;

CREATE OR REPLACE VIEW maintenance_open_ticket_summary AS
SELECT t.ticket_id,
       t.opened_at,
       t.priority,
       t.status,
       t.due_date,
       t.problem_summary,
       m.machine_name,
       m.asset_tag,
       COALESCE(l.name, m.location_name) AS location_name,
       t.assigned_to_name
FROM maintenance_tickets t
LEFT JOIN maintenance_machines m ON m.machine_id = t.machine_id
LEFT JOIN locations l ON l.location_id = m.location_id
WHERE t.status IN ('OPEN', 'IN_PROGRESS', 'WAITING_PARTS');

INSERT INTO permissions (permission_key, permission_name)
SELECT 'MAINTENANCE_MANAGEMENT', 'Maintenance Management'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'MAINTENANCE_MANAGEMENT'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'MACHINE_MANAGEMENT', 'Machine List'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'MACHINE_MANAGEMENT'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'PARTS_MANAGEMENT', 'Parts List'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'PARTS_MANAGEMENT'
);

INSERT INTO permissions (permission_key, permission_name, description, permission_group)
SELECT 'MAINTENANCE_TECHNICIAN',
       'Maintenance Technician',
       'Allows receiving open maintenance ticket notifications and working maintenance tickets.',
       'Inventory'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'MAINTENANCE_TECHNICIAN'
);

UPDATE permissions
SET permission_name = COALESCE(NULLIF(permission_name, ''), 'Maintenance Technician'),
    description = COALESCE(NULLIF(description, ''), 'Allows receiving open maintenance ticket notifications and working maintenance tickets.'),
    permission_group = COALESCE(NULLIF(permission_group, ''), 'Inventory')
WHERE UPPER(permission_key) = 'MAINTENANCE_TECHNICIAN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'MAINTENANCE_MANAGEMENT'
WHERE UPPER(r.role_name) = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) IN ('MACHINE_MANAGEMENT', 'PARTS_MANAGEMENT')
WHERE UPPER(r.role_name) = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
