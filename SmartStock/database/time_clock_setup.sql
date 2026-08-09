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


CREATE TABLE IF NOT EXISTS employee_payroll_settings (
    setting_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    period_type TEXT NOT NULL DEFAULT 'SEMI_MONTHLY',
    work_hour_limit NUMERIC(8,2) NOT NULL DEFAULT 80.00,
    effective_from DATE NOT NULL,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employee_payroll_settings_period_type_chk
        CHECK (period_type IN ('SEMI_MONTHLY', 'WEEKLY', 'FOUR_MONTH_BLOCKS')),
    CONSTRAINT employee_payroll_settings_hour_limit_chk CHECK (work_hour_limit > 0),
    CONSTRAINT employee_payroll_settings_user_effective_key UNIQUE (user_id, effective_from)
);

CREATE INDEX IF NOT EXISTS employee_payroll_settings_user_effective_idx
ON employee_payroll_settings(user_id, effective_from DESC);

CREATE OR REPLACE FUNCTION set_employee_payroll_settings_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS employee_payroll_settings_set_updated_at ON employee_payroll_settings;
CREATE TRIGGER employee_payroll_settings_set_updated_at
BEFORE UPDATE ON employee_payroll_settings
FOR EACH ROW EXECUTE FUNCTION set_employee_payroll_settings_updated_at();

INSERT INTO employee_payroll_settings (
    setting_id, user_id, period_type, work_hour_limit, effective_from, created_by_name
)
SELECT (
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 1, 8) || '-' ||
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 9, 4) || '-' ||
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 13, 4) || '-' ||
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 17, 4) || '-' ||
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 21, 12)
)::uuid,
u.user_id, 'SEMI_MONTHLY', 80.00, DATE '1900-01-01', 'System default'
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM employee_payroll_settings existing WHERE existing.user_id = u.user_id
);


DO $$
BEGIN
    ALTER TABLE public.employee_payroll_settings ENABLE ROW LEVEL SECURITY;
    REVOKE ALL ON TABLE public.employee_payroll_settings FROM PUBLIC;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.employee_payroll_settings FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.employee_payroll_settings FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON TABLE public.employee_payroll_settings TO service_role;
        DROP POLICY IF EXISTS employee_payroll_settings_service_role_all ON public.employee_payroll_settings;
        CREATE POLICY employee_payroll_settings_service_role_all
            ON public.employee_payroll_settings FOR ALL TO service_role
            USING (true) WITH CHECK (true);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS time_clock_auto_close_settings (
    settings_id UUID PRIMARY KEY,
    auto_close_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    scheduled_detection_delay_hours INTEGER NOT NULL DEFAULT 4,
    unscheduled_detection_hours INTEGER NOT NULL DEFAULT 12,
    max_auto_work_hours INTEGER NOT NULL DEFAULT 8,
    updated_by_user_id INTEGER REFERENCES users(user_id),
    updated_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT time_clock_auto_close_scheduled_delay_chk CHECK (scheduled_detection_delay_hours BETWEEN 0 AND 24),
    CONSTRAINT time_clock_auto_close_unscheduled_chk CHECK (unscheduled_detection_hours BETWEEN 1 AND 48),
    CONSTRAINT time_clock_auto_close_max_work_chk CHECK (max_auto_work_hours BETWEEN 1 AND 24),
    CONSTRAINT time_clock_auto_close_threshold_order_chk CHECK (unscheduled_detection_hours >= max_auto_work_hours)
);

INSERT INTO time_clock_auto_close_settings (
    settings_id, auto_close_enabled, scheduled_detection_delay_hours,
    unscheduled_detection_hours, max_auto_work_hours, updated_by_name
) VALUES ('8e56e4a5-742e-4f69-b819-2e853b850001'::uuid, TRUE, 4, 12, 8, 'System default')
ON CONFLICT (settings_id) DO NOTHING;

CREATE OR REPLACE FUNCTION set_time_clock_auto_close_settings_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS time_clock_auto_close_settings_updated_at ON time_clock_auto_close_settings;
CREATE TRIGGER time_clock_auto_close_settings_updated_at
BEFORE UPDATE ON time_clock_auto_close_settings
FOR EACH ROW EXECUTE FUNCTION set_time_clock_auto_close_settings_updated_at();

DO $$
BEGIN
    ALTER TABLE public.time_clock_auto_close_settings ENABLE ROW LEVEL SECURITY;
    REVOKE ALL ON TABLE public.time_clock_auto_close_settings FROM PUBLIC;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.time_clock_auto_close_settings FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.time_clock_auto_close_settings FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON TABLE public.time_clock_auto_close_settings TO service_role;
        DROP POLICY IF EXISTS time_clock_auto_close_settings_service_role_all ON public.time_clock_auto_close_settings;
        CREATE POLICY time_clock_auto_close_settings_service_role_all
            ON public.time_clock_auto_close_settings FOR ALL TO service_role USING (true) WITH CHECK (true);
    END IF;
END $$;



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
    break_start TIMESTAMPTZ,
    break_end TIMESTAMPTZ,
    clock_out TIMESTAMPTZ,
    total_hours_worked NUMERIC(10, 2),
    total_earned NUMERIC(12, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employee_time_clock_lunch_order CHECK (
        lunch_start IS NULL OR lunch_end IS NULL OR lunch_end >= lunch_start
    ),
    CONSTRAINT employee_time_clock_break_order CHECK (
        break_start IS NULL OR break_end IS NULL OR break_end >= break_start
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

ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS break_start TIMESTAMPTZ;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS break_end TIMESTAMPTZ;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_break_end BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_break_end_detected_at TIMESTAMPTZ;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_break_end_review_status TEXT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'employee_time_clock_break_order'
                   AND conrelid = 'employee_time_clock'::regclass) THEN
        ALTER TABLE employee_time_clock ADD CONSTRAINT employee_time_clock_break_order
            CHECK (break_start IS NULL OR break_end IS NULL OR break_end >= break_start);
    END IF;
END;
$$;

ALTER TABLE employee_time_clock
ADD COLUMN IF NOT EXISTS multiple_session_override_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE employee_time_clock
ADD COLUMN IF NOT EXISTS multiple_session_override_reason TEXT;

ALTER TABLE employee_time_clock
ADD COLUMN IF NOT EXISTS multiple_session_override_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE employee_time_clock
ADD COLUMN IF NOT EXISTS multiple_session_override_by_name TEXT;

ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_close_enabled_snapshot BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_close_rule_snapshot TEXT;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_close_detection_at TIMESTAMPTZ;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_close_max_work_hours INTEGER NOT NULL DEFAULT 8;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS scheduled_shift_id_snapshot UUID;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS scheduled_shift_name_snapshot TEXT;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS scheduled_shift_end_at_snapshot TIMESTAMPTZ;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_detected_at TIMESTAMPTZ;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_review_status TEXT;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_reviewed_at TIMESTAMPTZ;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_reviewed_by_user_id INTEGER REFERENCES users(user_id);
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_reviewed_by_name TEXT;
ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_review_reason TEXT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'employee_time_clock_auto_rule_chk'
                   AND conrelid = 'employee_time_clock'::regclass) THEN
        ALTER TABLE employee_time_clock ADD CONSTRAINT employee_time_clock_auto_rule_chk
            CHECK (auto_close_rule_snapshot IS NULL OR auto_close_rule_snapshot IN ('SCHEDULED', 'UNSCHEDULED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'employee_time_clock_auto_review_chk'
                   AND conrelid = 'employee_time_clock'::regclass) THEN
        ALTER TABLE employee_time_clock ADD CONSTRAINT employee_time_clock_auto_review_chk
            CHECK (auto_clock_out_review_status IS NULL OR auto_clock_out_review_status IN ('PENDING', 'CONFIRMED', 'CORRECTED'));
    END IF;
END $$;

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

CREATE INDEX IF NOT EXISTS employee_time_clock_auto_due_idx
ON employee_time_clock(auto_close_detection_at)
WHERE clock_out IS NULL AND auto_close_enabled_snapshot;

CREATE INDEX IF NOT EXISTS employee_time_clock_auto_review_idx
ON employee_time_clock(auto_clock_out_review_status, auto_clock_out_detected_at DESC)
WHERE auto_clock_out;

CREATE TABLE IF NOT EXISTS employee_time_clock_adjustments (
    adjustment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clock_id BIGINT NOT NULL REFERENCES employee_time_clock(clock_id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    action_type TEXT NOT NULL,
    before_clock_in TIMESTAMPTZ,
    before_lunch_start TIMESTAMPTZ,
    before_lunch_end TIMESTAMPTZ,
    before_break_start TIMESTAMPTZ,
    before_break_end TIMESTAMPTZ,
    before_clock_out TIMESTAMPTZ,
    before_hours NUMERIC(10,2),
    after_clock_in TIMESTAMPTZ,
    after_lunch_start TIMESTAMPTZ,
    after_lunch_end TIMESTAMPTZ,
    after_break_start TIMESTAMPTZ,
    after_break_end TIMESTAMPTZ,
    after_clock_out TIMESTAMPTZ,
    after_hours NUMERIC(10,2),
    reason TEXT NOT NULL,
    actor_user_id INTEGER REFERENCES users(user_id),
    actor_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employee_time_clock_adjustments_action_chk CHECK (action_type IN ('AUTO_CLOSE', 'BREAK_AUTO_END', 'CONFIRM', 'CORRECT'))
);

ALTER TABLE employee_time_clock_adjustments ADD COLUMN IF NOT EXISTS before_break_start TIMESTAMPTZ;
ALTER TABLE employee_time_clock_adjustments ADD COLUMN IF NOT EXISTS before_break_end TIMESTAMPTZ;
ALTER TABLE employee_time_clock_adjustments ADD COLUMN IF NOT EXISTS after_break_start TIMESTAMPTZ;
ALTER TABLE employee_time_clock_adjustments ADD COLUMN IF NOT EXISTS after_break_end TIMESTAMPTZ;
ALTER TABLE employee_time_clock_adjustments DROP CONSTRAINT IF EXISTS employee_time_clock_adjustments_action_chk;
ALTER TABLE employee_time_clock_adjustments ADD CONSTRAINT employee_time_clock_adjustments_action_chk
    CHECK (action_type IN ('AUTO_CLOSE', 'BREAK_AUTO_END', 'CONFIRM', 'CORRECT'));

CREATE INDEX IF NOT EXISTS employee_time_clock_adjustments_clock_idx
ON employee_time_clock_adjustments(clock_id, created_at DESC);

CREATE OR REPLACE FUNCTION prevent_employee_time_clock_adjustment_changes()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Time-clock adjustment history is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS employee_time_clock_adjustments_append_only ON employee_time_clock_adjustments;
CREATE TRIGGER employee_time_clock_adjustments_append_only
BEFORE UPDATE OR DELETE ON employee_time_clock_adjustments
FOR EACH ROW EXECUTE FUNCTION prevent_employee_time_clock_adjustment_changes();

DO $$
BEGIN
    ALTER TABLE public.employee_time_clock_adjustments ENABLE ROW LEVEL SECURITY;
    REVOKE ALL ON TABLE public.employee_time_clock_adjustments FROM PUBLIC;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.employee_time_clock_adjustments FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.employee_time_clock_adjustments FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON TABLE public.employee_time_clock_adjustments TO service_role;
        DROP POLICY IF EXISTS employee_time_clock_adjustments_service_role_all ON public.employee_time_clock_adjustments;
        CREATE POLICY employee_time_clock_adjustments_service_role_all
            ON public.employee_time_clock_adjustments FOR ALL TO service_role USING (true) WITH CHECK (true);
    END IF;
END $$;

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
    pay_period_type TEXT NOT NULL DEFAULT 'SEMI_MONTHLY',
    work_hour_limit NUMERIC(8, 2) NOT NULL DEFAULT 80.00,
    regular_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    overtime_hours NUMERIC(10, 2) NOT NULL DEFAULT 0,
    regular_pay NUMERIC(12, 2) NOT NULL DEFAULT 0,
    overtime_pay NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_pay NUMERIC(12, 2) NOT NULL DEFAULT 0,
    record_count INTEGER NOT NULL DEFAULT 0,
    compensation_type TEXT,
    location_name TEXT,
    payment_method TEXT NOT NULL DEFAULT 'CASH',
    payment_reference TEXT,
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

ALTER TABLE payroll_payments
ADD COLUMN IF NOT EXISTS payment_method TEXT NOT NULL DEFAULT 'CASH';

ALTER TABLE payroll_payments
ADD COLUMN IF NOT EXISTS payment_reference TEXT;

ALTER TABLE payroll_payments ADD COLUMN IF NOT EXISTS pay_period_type TEXT NOT NULL DEFAULT 'SEMI_MONTHLY';
ALTER TABLE payroll_payments ADD COLUMN IF NOT EXISTS work_hour_limit NUMERIC(8, 2) NOT NULL DEFAULT 80.00;
ALTER TABLE payroll_payments ADD COLUMN IF NOT EXISTS regular_hours NUMERIC(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE payroll_payments ADD COLUMN IF NOT EXISTS overtime_hours NUMERIC(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE payroll_payments ADD COLUMN IF NOT EXISTS regular_pay NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE payroll_payments ADD COLUMN IF NOT EXISTS overtime_pay NUMERIC(12, 2) NOT NULL DEFAULT 0;

DROP INDEX IF EXISTS payroll_payments_employee_period_idx;

CREATE UNIQUE INDEX IF NOT EXISTS payroll_payments_employee_period_payment_idx
ON payroll_payments(user_id, pay_period_start, pay_period_end, payment_number);

CREATE INDEX IF NOT EXISTS payroll_payments_location_paid_idx
ON payroll_payments(location_id, paid_at DESC);

CREATE TABLE IF NOT EXISTS employee_payroll_bonuses (
    employee_payroll_bonus_id BIGSERIAL PRIMARY KEY,
    sync_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    location_id INTEGER REFERENCES locations(location_id),
    employee_name TEXT,
    pay_period_start DATE NOT NULL,
    pay_period_end DATE NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    reason TEXT,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employee_payroll_bonuses_amount_chk CHECK (amount > 0),
    CONSTRAINT employee_payroll_bonuses_sync_uuid_key UNIQUE (sync_uuid)
);

CREATE INDEX IF NOT EXISTS employee_payroll_bonuses_period_idx
ON employee_payroll_bonuses(pay_period_start, pay_period_end, user_id);

CREATE INDEX IF NOT EXISTS employee_payroll_bonuses_location_period_idx
ON employee_payroll_bonuses(location_id, pay_period_start, pay_period_end);

DO $$
BEGIN
    ALTER TABLE public.employee_payroll_bonuses ENABLE ROW LEVEL SECURITY;
    REVOKE ALL ON TABLE public.employee_payroll_bonuses FROM PUBLIC;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.employee_payroll_bonuses FROM anon;
        REVOKE ALL ON SEQUENCE public.employee_payroll_bonuses_employee_payroll_bonus_id_seq FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.employee_payroll_bonuses FROM authenticated;
        REVOKE ALL ON SEQUENCE public.employee_payroll_bonuses_employee_payroll_bonus_id_seq FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON TABLE public.employee_payroll_bonuses TO service_role;
        GRANT ALL ON SEQUENCE public.employee_payroll_bonuses_employee_payroll_bonus_id_seq TO service_role;
        DROP POLICY IF EXISTS employee_payroll_bonuses_service_role_all ON public.employee_payroll_bonuses;
        CREATE POLICY employee_payroll_bonuses_service_role_all
            ON public.employee_payroll_bonuses FOR ALL TO service_role
            USING (true) WITH CHECK (true);
    END IF;
END $$;

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
