CREATE TABLE IF NOT EXISTS public.smartstock_first_admin_setup (
    setup_key text PRIMARY KEY,
    email text NOT NULL,
    auth_user_id uuid NOT NULL,
    production_user_id integer,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamp with time zone
);

REVOKE ALL ON TABLE public.smartstock_first_admin_setup FROM PUBLIC;
