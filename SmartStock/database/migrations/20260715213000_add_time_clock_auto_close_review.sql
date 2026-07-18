-- Company-wide stale time-clock closure policy and append-only review history.

CREATE TABLE IF NOT EXISTS public.time_clock_auto_close_settings (
    settings_id UUID PRIMARY KEY,
    auto_close_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    scheduled_detection_delay_hours INTEGER NOT NULL DEFAULT 4 CHECK (scheduled_detection_delay_hours BETWEEN 0 AND 24),
    unscheduled_detection_hours INTEGER NOT NULL DEFAULT 12 CHECK (unscheduled_detection_hours BETWEEN 1 AND 48),
    max_auto_work_hours INTEGER NOT NULL DEFAULT 8 CHECK (max_auto_work_hours BETWEEN 1 AND 24),
    updated_by_user_id INTEGER REFERENCES public.users(user_id),
    updated_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT time_clock_auto_close_threshold_order_chk CHECK (unscheduled_detection_hours >= max_auto_work_hours)
);

INSERT INTO public.time_clock_auto_close_settings (
    settings_id, auto_close_enabled, scheduled_detection_delay_hours,
    unscheduled_detection_hours, max_auto_work_hours, updated_by_name
) VALUES ('8e56e4a5-742e-4f69-b819-2e853b850001'::uuid, TRUE, 4, 12, 8, 'System default')
ON CONFLICT (settings_id) DO NOTHING;

CREATE OR REPLACE FUNCTION public.set_time_clock_auto_close_settings_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS time_clock_auto_close_settings_updated_at ON public.time_clock_auto_close_settings;
CREATE TRIGGER time_clock_auto_close_settings_updated_at
BEFORE UPDATE ON public.time_clock_auto_close_settings
FOR EACH ROW EXECUTE FUNCTION public.set_time_clock_auto_close_settings_updated_at();

ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_close_enabled_snapshot BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_close_rule_snapshot TEXT;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_close_detection_at TIMESTAMPTZ;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_close_max_work_hours INTEGER NOT NULL DEFAULT 8;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS scheduled_shift_id_snapshot UUID;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS scheduled_shift_name_snapshot TEXT;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS scheduled_shift_end_at_snapshot TIMESTAMPTZ;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_detected_at TIMESTAMPTZ;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_review_status TEXT;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_reviewed_at TIMESTAMPTZ;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_reviewed_by_user_id INTEGER REFERENCES public.users(user_id);
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_reviewed_by_name TEXT;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_review_reason TEXT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'employee_time_clock_auto_rule_chk'
                   AND conrelid = 'public.employee_time_clock'::regclass) THEN
        ALTER TABLE public.employee_time_clock ADD CONSTRAINT employee_time_clock_auto_rule_chk
            CHECK (auto_close_rule_snapshot IS NULL OR auto_close_rule_snapshot IN ('SCHEDULED', 'UNSCHEDULED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'employee_time_clock_auto_review_chk'
                   AND conrelid = 'public.employee_time_clock'::regclass) THEN
        ALTER TABLE public.employee_time_clock ADD CONSTRAINT employee_time_clock_auto_review_chk
            CHECK (auto_clock_out_review_status IS NULL OR auto_clock_out_review_status IN ('PENDING', 'CONFIRMED', 'CORRECTED'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS employee_time_clock_auto_due_idx
ON public.employee_time_clock(auto_close_detection_at)
WHERE clock_out IS NULL AND auto_close_enabled_snapshot;

CREATE INDEX IF NOT EXISTS employee_time_clock_auto_review_idx
ON public.employee_time_clock(auto_clock_out_review_status, auto_clock_out_detected_at DESC)
WHERE auto_clock_out;

CREATE TABLE IF NOT EXISTS public.employee_time_clock_adjustments (
    adjustment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clock_id BIGINT NOT NULL REFERENCES public.employee_time_clock(clock_id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    action_type TEXT NOT NULL CHECK (action_type IN ('AUTO_CLOSE', 'CONFIRM', 'CORRECT')),
    before_clock_in TIMESTAMPTZ,
    before_lunch_start TIMESTAMPTZ,
    before_lunch_end TIMESTAMPTZ,
    before_clock_out TIMESTAMPTZ,
    before_hours NUMERIC(10,2),
    after_clock_in TIMESTAMPTZ,
    after_lunch_start TIMESTAMPTZ,
    after_lunch_end TIMESTAMPTZ,
    after_clock_out TIMESTAMPTZ,
    after_hours NUMERIC(10,2),
    reason TEXT NOT NULL,
    actor_user_id INTEGER REFERENCES public.users(user_id),
    actor_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS employee_time_clock_adjustments_clock_idx
ON public.employee_time_clock_adjustments(clock_id, created_at DESC);

CREATE OR REPLACE FUNCTION public.prevent_employee_time_clock_adjustment_changes()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Time-clock adjustment history is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS employee_time_clock_adjustments_append_only ON public.employee_time_clock_adjustments;
CREATE TRIGGER employee_time_clock_adjustments_append_only
BEFORE UPDATE OR DELETE ON public.employee_time_clock_adjustments
FOR EACH ROW EXECUTE FUNCTION public.prevent_employee_time_clock_adjustment_changes();

DO $$
BEGIN
    ALTER TABLE public.time_clock_auto_close_settings ENABLE ROW LEVEL SECURITY;
    ALTER TABLE public.employee_time_clock_adjustments ENABLE ROW LEVEL SECURITY;
    REVOKE ALL ON TABLE public.time_clock_auto_close_settings, public.employee_time_clock_adjustments FROM PUBLIC;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.time_clock_auto_close_settings, public.employee_time_clock_adjustments FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.time_clock_auto_close_settings, public.employee_time_clock_adjustments FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON TABLE public.time_clock_auto_close_settings, public.employee_time_clock_adjustments TO service_role;
        DROP POLICY IF EXISTS time_clock_auto_close_settings_service_role_all ON public.time_clock_auto_close_settings;
        CREATE POLICY time_clock_auto_close_settings_service_role_all ON public.time_clock_auto_close_settings
            FOR ALL TO service_role USING (true) WITH CHECK (true);
        DROP POLICY IF EXISTS employee_time_clock_adjustments_service_role_all ON public.employee_time_clock_adjustments;
        CREATE POLICY employee_time_clock_adjustments_service_role_all ON public.employee_time_clock_adjustments
            FOR ALL TO service_role USING (true) WITH CHECK (true);
    END IF;
END $$;
