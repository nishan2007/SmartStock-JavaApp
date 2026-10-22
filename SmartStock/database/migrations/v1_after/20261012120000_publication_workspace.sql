-- Local store only: publications and their embedded assets never become register credentials.
CREATE TABLE IF NOT EXISTS public.publication_projects (
 project_id uuid PRIMARY KEY,
 location_id integer NOT NULL REFERENCES public.locations(location_id),
 title text NOT NULL CHECK (length(title) BETWEEN 1 AND 200),
 is_template boolean NOT NULL DEFAULT false,
 order_id bigint REFERENCES public.custom_orders(custom_order_id),
 line_id bigint REFERENCES public.custom_order_lines(custom_order_line_id),
 revision integer NOT NULL DEFAULT 1 CHECK (revision > 0),
 archive bytea NOT NULL CHECK (octet_length(archive) <= 50331648),
 updated_by integer NOT NULL REFERENCES public.users(user_id),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_publication_projects_location_updated
 ON public.publication_projects(location_id,updated_at DESC,project_id);
CREATE INDEX IF NOT EXISTS idx_publication_projects_order
 ON public.publication_projects(location_id,order_id,line_id) WHERE order_id IS NOT NULL;
CREATE TABLE IF NOT EXISTS public.publication_uploads (
 upload_id uuid PRIMARY KEY,
 location_id integer NOT NULL REFERENCES public.locations(location_id),
 user_id integer NOT NULL REFERENCES public.users(user_id),
 project_id uuid NOT NULL,
 expected_revision integer NOT NULL CHECK (expected_revision >= 0),
 archive bytea NOT NULL DEFAULT ''::bytea CHECK (octet_length(archive) <= 50331648),
 expires_at timestamptz NOT NULL DEFAULT (now() + interval '1 hour')
);
CREATE INDEX IF NOT EXISTS idx_publication_uploads_expiry ON public.publication_uploads(expires_at);
