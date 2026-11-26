CREATE TABLE IF NOT EXISTS public.payroll_web_requests (
 browser_id uuid NOT NULL,user_id integer NOT NULL REFERENCES public.users(user_id),request_key uuid NOT NULL,
 request_hash text NOT NULL,response jsonb,created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(browser_id,user_id,request_key));
CREATE INDEX IF NOT EXISTS payroll_web_requests_created_idx ON public.payroll_web_requests(created_at);
ALTER TABLE public.payroll_web_requests ENABLE ROW LEVEL SECURITY;
