ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS break_start TIMESTAMPTZ;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS break_end TIMESTAMPTZ;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_break_end BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_break_end_detected_at TIMESTAMPTZ;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS auto_break_end_review_status TEXT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'employee_time_clock_break_order'
                   AND conrelid = 'public.employee_time_clock'::regclass) THEN
        ALTER TABLE public.employee_time_clock ADD CONSTRAINT employee_time_clock_break_order
            CHECK (break_start IS NULL OR break_end IS NULL OR break_end >= break_start);
    END IF;
END;
$$;

ALTER TABLE public.employee_time_clock_adjustments ADD COLUMN IF NOT EXISTS before_break_start TIMESTAMPTZ;
ALTER TABLE public.employee_time_clock_adjustments ADD COLUMN IF NOT EXISTS before_break_end TIMESTAMPTZ;
ALTER TABLE public.employee_time_clock_adjustments ADD COLUMN IF NOT EXISTS after_break_start TIMESTAMPTZ;
ALTER TABLE public.employee_time_clock_adjustments ADD COLUMN IF NOT EXISTS after_break_end TIMESTAMPTZ;

ALTER TABLE public.employee_time_clock_adjustments
    DROP CONSTRAINT IF EXISTS employee_time_clock_adjustments_action_chk;
ALTER TABLE public.employee_time_clock_adjustments
    ADD CONSTRAINT employee_time_clock_adjustments_action_chk
    CHECK (action_type IN ('AUTO_CLOSE', 'BREAK_AUTO_END', 'CONFIRM', 'CORRECT'));

CREATE INDEX IF NOT EXISTS employee_time_clock_open_break_due_idx
    ON public.employee_time_clock(break_start)
    WHERE clock_out IS NULL AND break_start IS NOT NULL AND break_end IS NULL;
