ALTER TABLE public.register_transfers ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.register_transfers FROM anon;
REVOKE ALL ON TABLE public.register_transfers FROM authenticated;
