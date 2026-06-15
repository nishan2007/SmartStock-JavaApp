-- Time Clock setup for SmartStock.
-- Run this in Supabase SQL Editor before using the Time Clock screen.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'compensation_type_enum') THEN
        CREATE TYPE compensation_type_enum AS ENUM ('HOURLY', 'SALARY', 'DAILY');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'pay_period_type_enum') THEN
        CREATE TYPE pay_period_type_enum AS ENUM ('SEMI_MONTHLY', 'WEEKLY');
    END IF;
END $$;

ALTER TYPE compensation_type_enum ADD VALUE IF NOT EXISTS 'DAILY';

ALTER TABLE users
ADD COLUMN IF NOT EXISTS compensation_type compensation_type_enum NOT NULL DEFAULT 'HOURLY';

ALTER TABLE users
ADD COLUMN IF NOT EXISTS salary NUMERIC(10, 2) NOT NULL DEFAULT 0;

ALTER TABLE users
DROP COLUMN IF EXISTS hourly_wage;

ALTER TABLE users
DROP COLUMN IF EXISTS salary_amount;

ALTER TABLE users
DROP COLUMN IF EXISTS daily_salary;

ALTER TABLE users
DROP COLUMN IF EXISTS pay_period_type;

ALTER TABLE users
DROP CONSTRAINT IF EXISTS users_compensation_type_check;

ALTER TABLE users
ALTER COLUMN compensation_type DROP DEFAULT;

ALTER TABLE users
ALTER COLUMN compensation_type TYPE compensation_type_enum
USING UPPER(compensation_type::TEXT)::compensation_type_enum;

ALTER TABLE users
ALTER COLUMN compensation_type SET DEFAULT 'HOURLY';

CREATE TABLE IF NOT EXISTS employee_time_clock (
    clock_id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    user_name TEXT,
    location_id INTEGER REFERENCES locations(location_id),
    location_name TEXT,
    work_date DATE NOT NULL DEFAULT CURRENT_DATE,
    clock_in TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lunch_start TIMESTAMPTZ,
    lunch_end TIMESTAMPTZ,
    clock_out TIMESTAMPTZ,
    total_hours_worked NUMERIC(10, 2),
    total_earned NUMERIC(12, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employee_time_clock_lunch_order CHECK (
        lunch_start IS NULL OR lunch_end IS NULL OR lunch_end >= lunch_start
    ),
    CONSTRAINT employee_time_clock_out_order CHECK (
        clock_out IS NULL OR clock_out >= clock_in
    )
);

ALTER TABLE employee_time_clock
ADD COLUMN IF NOT EXISTS total_hours_worked NUMERIC(10, 2);

ALTER TABLE employee_time_clock
ADD COLUMN IF NOT EXISTS total_earned NUMERIC(12, 2);

ALTER TABLE employee_time_clock
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE employee_time_clock
ADD COLUMN IF NOT EXISTS multiple_session_override_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE employee_time_clock
ADD COLUMN IF NOT EXISTS multiple_session_override_reason TEXT;

ALTER TABLE employee_time_clock
ADD COLUMN IF NOT EXISTS multiple_session_override_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE employee_time_clock
ADD COLUMN IF NOT EXISTS multiple_session_override_by_name TEXT;

CREATE OR REPLACE FUNCTION set_employee_time_clock_updated_at()
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

DROP TRIGGER IF EXISTS employee_time_clock_set_updated_at ON employee_time_clock;
CREATE TRIGGER employee_time_clock_set_updated_at
BEFORE INSERT OR UPDATE ON employee_time_clock
FOR EACH ROW
EXECUTE FUNCTION set_employee_time_clock_updated_at();

CREATE INDEX IF NOT EXISTS employee_time_clock_updated_at_idx
ON employee_time_clock(updated_at DESC);

ALTER TABLE employee_time_clock
DROP CONSTRAINT IF EXISTS employee_time_clock_compensation_type_check;

ALTER TABLE employee_time_clock
DROP COLUMN IF EXISTS compensation_type;

ALTER TABLE employee_time_clock
DROP COLUMN IF EXISTS pay_period_type;

ALTER TABLE employee_time_clock
DROP COLUMN IF EXISTS hourly_wage;

ALTER TABLE employee_time_clock
DROP COLUMN IF EXISTS salary_amount;

ALTER TABLE employee_time_clock
DROP COLUMN IF EXISTS daily_salary;

DROP INDEX IF EXISTS employee_time_clock_one_open_shift;

CREATE UNIQUE INDEX IF NOT EXISTS employee_time_clock_one_open_shift_per_user
ON employee_time_clock(user_id)
WHERE clock_out IS NULL;

CREATE INDEX IF NOT EXISTS employee_time_clock_user_date_idx
ON employee_time_clock(user_id, work_date DESC);

CREATE TABLE IF NOT EXISTS payroll_payments (
    payroll_payment_id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    employee_name TEXT,
    employee_role TEXT,
    location_id INTEGER REFERENCES locations(location_id),
    pay_period_start DATE NOT NULL,
    pay_period_end DATE NOT NULL,
    payment_number INTEGER NOT NULL DEFAULT 1,
    pay_date DATE,
    days_worked INTEGER NOT NULL DEFAULT 0,
    total_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    total_pay NUMERIC(12, 2) NOT NULL DEFAULT 0,
    record_count INTEGER NOT NULL DEFAULT 0,
    compensation_type TEXT,
    location_name TEXT,
    paid_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_by_user_id INTEGER REFERENCES users(user_id),
    paid_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE payroll_payments
ADD COLUMN IF NOT EXISTS days_worked INTEGER NOT NULL DEFAULT 0;

ALTER TABLE payroll_payments
ADD COLUMN IF NOT EXISTS location_id INTEGER REFERENCES locations(location_id);

ALTER TABLE payroll_payments
ADD COLUMN IF NOT EXISTS payment_number INTEGER NOT NULL DEFAULT 1;

DROP INDEX IF EXISTS payroll_payments_employee_period_idx;

CREATE UNIQUE INDEX IF NOT EXISTS payroll_payments_employee_period_payment_idx
ON payroll_payments(user_id, pay_period_start, pay_period_end, payment_number);

CREATE INDEX IF NOT EXISTS payroll_payments_location_paid_idx
ON payroll_payments(location_id, paid_at DESC);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'TIME_CLOCK', 'Time Clock'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'TIME_CLOCK'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'TIME_CLOCK_MANAGEMENT', 'Time Clock Management'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'TIME_CLOCK_MANAGEMENT'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'TIME_CLOCK_OVERRIDE', 'Time Clock Override'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'TIME_CLOCK_OVERRIDE'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'PAYROLL_DASHBOARD', 'Payroll Dashboard'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'PAYROLL_DASHBOARD'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) IN ('TIME_CLOCK', 'TIME_CLOCK_MANAGEMENT', 'TIME_CLOCK_OVERRIDE', 'PAYROLL_DASHBOARD')
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
