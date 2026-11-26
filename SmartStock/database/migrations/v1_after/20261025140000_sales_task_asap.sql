ALTER TABLE public.sales_tasks ADD COLUMN IF NOT EXISTS due_asap boolean NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS sales_tasks_asap_queue_idx ON public.sales_tasks(location_id,due_asap DESC,due_at,task_id)
WHERE status NOT IN ('COLLECTED','CANCELLED');
