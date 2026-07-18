-- Supports auto-scheduler cross-store conflict and recent-history lookups.
CREATE INDEX IF NOT EXISTS employee_schedule_user_date_idx
ON employee_schedule_assignments(user_id, work_date);
