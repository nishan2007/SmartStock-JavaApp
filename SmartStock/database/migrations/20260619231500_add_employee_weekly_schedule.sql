ALTER TABLE permissions ADD COLUMN IF NOT EXISTS permission_name TEXT;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS permission_group TEXT;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS permission_subgroup TEXT;

CREATE TABLE IF NOT EXISTS employee_schedule_assignments (
    location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    work_date DATE NOT NULL,
    lunch_start_time TIME,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (location_id, user_id, work_date)
);

CREATE INDEX IF NOT EXISTS employee_schedule_location_date_idx
ON employee_schedule_assignments(location_id, work_date);

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
    ('EDIT_EMPLOYEE_SCHEDULE', 'Edit Employee Schedule', 'Allows adding and removing employees from the weekly schedule.', 'People', 'Scheduling')
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
FROM roles r CROSS JOIN permissions p
WHERE (UPPER(r.role_name) IN ('ADMIN', 'CEO') OR UPPER(r.role_name) LIKE '%MANAGER%')
  AND UPPER(p.permission_key) = 'EDIT_EMPLOYEE_SCHEDULE'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      JOIN permissions existing_permission ON existing_permission.permission_id = existing.permission_id
      WHERE UPPER(existing_permission.permission_key) = 'EDIT_EMPLOYEE_SCHEDULE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
