-- Weekly employee schedule and role permissions.
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS permission_name TEXT;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS permission_group TEXT;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS permission_subgroup TEXT;

CREATE TABLE IF NOT EXISTS employee_schedule_shifts (
    shift_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
    shift_name TEXT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    updated_by_user_id INTEGER REFERENCES users(user_id),
    updated_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employee_schedule_shifts_daytime_check CHECK (end_time > start_time),
    CONSTRAINT employee_schedule_shifts_location_identity UNIQUE (location_id, shift_id)
);

ALTER TABLE employee_schedule_shifts ADD COLUMN IF NOT EXISTS updated_by_user_id INTEGER REFERENCES users(user_id);
ALTER TABLE employee_schedule_shifts ADD COLUMN IF NOT EXISTS updated_by_name TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS employee_schedule_shifts_location_name_idx
ON employee_schedule_shifts(location_id, LOWER(TRIM(shift_name)));

CREATE INDEX IF NOT EXISTS employee_schedule_shifts_location_order_idx
ON employee_schedule_shifts(location_id, is_active DESC, display_order, start_time);

ALTER TABLE employee_schedule_shifts ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON employee_schedule_shifts FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON employee_schedule_shifts FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON employee_schedule_shifts TO service_role;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION set_employee_schedule_shifts_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS employee_schedule_shifts_set_updated_at ON employee_schedule_shifts;
CREATE TRIGGER employee_schedule_shifts_set_updated_at
BEFORE INSERT OR UPDATE ON employee_schedule_shifts
FOR EACH ROW EXECUTE FUNCTION set_employee_schedule_shifts_updated_at();

CREATE TABLE IF NOT EXISTS employee_schedule_holidays (
    holiday_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_date DATE NOT NULL UNIQUE,
    holiday_name TEXT NOT NULL DEFAULT 'Holiday',
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    updated_by_user_id INTEGER REFERENCES users(user_id),
    updated_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employee_schedule_holidays_name_chk CHECK (LENGTH(TRIM(holiday_name)) > 0)
);

CREATE INDEX IF NOT EXISTS employee_schedule_holidays_date_idx
ON employee_schedule_holidays(holiday_date);

ALTER TABLE employee_schedule_holidays ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    REVOKE ALL ON TABLE public.employee_schedule_holidays FROM PUBLIC;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON employee_schedule_holidays FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON employee_schedule_holidays FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON employee_schedule_holidays TO service_role;
        DROP POLICY IF EXISTS employee_schedule_holidays_service_role_all ON employee_schedule_holidays;
        CREATE POLICY employee_schedule_holidays_service_role_all
            ON employee_schedule_holidays FOR ALL TO service_role USING (true) WITH CHECK (true);
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION set_employee_schedule_holidays_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS employee_schedule_holidays_set_updated_at ON employee_schedule_holidays;
CREATE TRIGGER employee_schedule_holidays_set_updated_at
BEFORE INSERT OR UPDATE ON employee_schedule_holidays
FOR EACH ROW EXECUTE FUNCTION set_employee_schedule_holidays_updated_at();

INSERT INTO employee_schedule_shifts (
    shift_id, location_id, shift_name, start_time, end_time, display_order
)
SELECT (md5('employee-schedule-shift:' || l.location_id || ':0700-1600'))::uuid,
       l.location_id, '7 AM–4 PM', TIME '07:00', TIME '16:00', 10
FROM locations l
ON CONFLICT (shift_id) DO NOTHING;

INSERT INTO employee_schedule_shifts (
    shift_id, location_id, shift_name, start_time, end_time, display_order
)
SELECT (md5('employee-schedule-shift:' || l.location_id || ':0900-1800'))::uuid,
       l.location_id, '9 AM–6 PM', TIME '09:00', TIME '18:00', 20
FROM locations l
ON CONFLICT (shift_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS employee_schedule_assignments (
    location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    work_date DATE NOT NULL,
    lunch_start_time TIME,
    shift_id UUID,
    shift_name_snapshot TEXT,
    shift_start_time TIME,
    shift_end_time TIME,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (location_id, user_id, work_date),
    CONSTRAINT employee_schedule_assignments_location_shift_fk FOREIGN KEY (location_id, shift_id)
        REFERENCES employee_schedule_shifts(location_id, shift_id)
);

CREATE INDEX IF NOT EXISTS employee_schedule_location_date_idx
ON employee_schedule_assignments(location_id, work_date);

CREATE INDEX IF NOT EXISTS employee_schedule_user_date_idx
ON employee_schedule_assignments(user_id, work_date);

ALTER TABLE employee_schedule_assignments
ADD COLUMN IF NOT EXISTS lunch_start_time TIME;

ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS shift_id UUID;
ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS shift_name_snapshot TEXT;
ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS shift_start_time TIME;
ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS shift_end_time TIME;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'employee_schedule_assignments_location_shift_fk'
    ) THEN
        ALTER TABLE employee_schedule_assignments
        ADD CONSTRAINT employee_schedule_assignments_location_shift_fk
        FOREIGN KEY (location_id, shift_id)
        REFERENCES employee_schedule_shifts(location_id, shift_id);
    END IF;
END;
$$;

ALTER TABLE employee_schedule_assignments ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON employee_schedule_assignments FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON employee_schedule_assignments FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON employee_schedule_assignments TO service_role;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION set_employee_schedule_assignments_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS employee_schedule_assignments_set_updated_at ON employee_schedule_assignments;
CREATE TRIGGER employee_schedule_assignments_set_updated_at
BEFORE INSERT OR UPDATE ON employee_schedule_assignments
FOR EACH ROW EXECUTE FUNCTION set_employee_schedule_assignments_updated_at();

INSERT INTO permissions (permission_key, permission_name, description, permission_group, permission_subgroup)
VALUES
    ('VIEW_EMPLOYEE_SCHEDULE', 'View Employee Schedule', 'Allows viewing who is scheduled to work each day.', 'People', 'Scheduling'),
    ('EDIT_EMPLOYEE_SCHEDULE', 'Edit Employee Schedule', 'Allows adding and removing employees from the weekly schedule.', 'People', 'Scheduling'),
    ('SCHEDULE_OTHER_STORES', 'Schedule Other Stores', 'Allows viewing and scheduling employees at stores other than the selected login store.', 'People', 'Scheduling')
ON CONFLICT (permission_key) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    description = EXCLUDED.description,
    permission_group = EXCLUDED.permission_group,
    permission_subgroup = EXCLUDED.permission_subgroup;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r CROSS JOIN permissions p
WHERE UPPER(p.permission_key) = 'VIEW_EMPLOYEE_SCHEDULE'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      JOIN permissions existing_permission ON existing_permission.permission_id = existing.permission_id
      WHERE UPPER(existing_permission.permission_key) = 'VIEW_EMPLOYEE_SCHEDULE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'SCHEDULE_OTHER_STORES'
WHERE UPPER(r.role_name) = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r CROSS JOIN permissions p
WHERE (UPPER(r.role_name) IN ('ADMIN', 'CEO') OR UPPER(r.role_name) LIKE '%MANAGER%')
  AND UPPER(p.permission_key) = 'EDIT_EMPLOYEE_SCHEDULE'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      JOIN permissions existing_permission ON existing_permission.permission_id = existing.permission_id
      WHERE UPPER(existing_permission.permission_key) = 'EDIT_EMPLOYEE_SCHEDULE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
