ALTER TABLE employee_schedule_assignments
ADD COLUMN IF NOT EXISTS lunch_start_time TIME;
