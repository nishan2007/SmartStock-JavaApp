-- Store key custody is local-server authority; cloud snapshots are recovery only.
CREATE TABLE IF NOT EXISTS public.store_keys (
 key_id uuid PRIMARY KEY, location_id integer NOT NULL REFERENCES public.locations(location_id),
 key_number integer NOT NULL CHECK(key_number>0), label text NOT NULL,
 retired boolean NOT NULL DEFAULT false, card_hash text UNIQUE,
 version bigint NOT NULL DEFAULT 0, updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(location_id,key_number), UNIQUE(key_id,location_id)
);
CREATE TABLE IF NOT EXISTS public.store_key_custody (
 custody_id uuid PRIMARY KEY, key_id uuid NOT NULL, location_id integer NOT NULL,
 holder_user_id integer NOT NULL REFERENCES public.users(user_id),
 checked_out_by integer NOT NULL REFERENCES public.users(user_id), checked_out_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 returned_by integer REFERENCES public.users(user_id), returned_at timestamptz,
 return_kind text CHECK(return_kind IN ('RETURN','FORCED_RETURN')), return_reason text,
 FOREIGN KEY(key_id,location_id) REFERENCES public.store_keys(key_id,location_id),
 CHECK((returned_at IS NULL AND returned_by IS NULL AND return_kind IS NULL) OR
       (returned_at IS NOT NULL AND returned_by IS NOT NULL AND return_kind IS NOT NULL)),
 CHECK(return_kind IS DISTINCT FROM 'FORCED_RETURN' OR (return_reason IS NOT NULL AND length(trim(return_reason))>0))
);
CREATE UNIQUE INDEX IF NOT EXISTS store_key_one_holder ON public.store_key_custody(key_id) WHERE returned_at IS NULL;
CREATE INDEX IF NOT EXISTS store_key_holder_lookup ON public.store_key_custody(location_id,holder_user_id,checked_out_at DESC);
CREATE TABLE IF NOT EXISTS public.store_key_events (
 event_id uuid PRIMARY KEY, key_id uuid NOT NULL, location_id integer NOT NULL,
 custody_id uuid REFERENCES public.store_key_custody(custody_id),
 event_type text NOT NULL CHECK(event_type IN ('CREATE','LABEL','PAIR','RETIRE','CHECKOUT','RETURN','FORCED_RETURN')),
 actor_user_id integer NOT NULL REFERENCES public.users(user_id), actor_name text NOT NULL,
 holder_user_id integer REFERENCES public.users(user_id), holder_name text,
 method text NOT NULL CHECK(method IN ('MANUAL','NFC')), reason text NOT NULL DEFAULT '',
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(key_id,location_id) REFERENCES public.store_keys(key_id,location_id)
);
CREATE INDEX IF NOT EXISTS store_key_history_lookup ON public.store_key_events(location_id,created_at DESC,event_id);
CREATE INDEX IF NOT EXISTS store_key_history_key ON public.store_key_events(key_id,created_at DESC);
CREATE OR REPLACE FUNCTION public.store_key_events_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Store key history is append-only'; END; $$;
DROP TRIGGER IF EXISTS store_key_events_immutable ON public.store_key_events;
CREATE TRIGGER store_key_events_immutable BEFORE UPDATE OR DELETE ON public.store_key_events
 FOR EACH ROW EXECUTE FUNCTION public.store_key_events_immutable();
INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup) VALUES
 ('STORE_KEYS_SELF','Use Store Keys','Sign store keys out and return your own keys.','Employee','Store Keys'),
 ('STORE_KEYS_ASSIST','Record Staff Key Transactions','Record key checkout and return on behalf of employees.','Employee','Store Keys'),
 ('STORE_KEYS_CONFIGURE','Configure Store Keys','Configure numbered keys and pair NFC cards.','Administration','Store Keys'),
 ('STORE_KEYS_FORCE_RETURN','Recover Store Keys','Record physical recovery of a key without the holder present.','Administration','Store Keys'),
 ('STORE_KEYS_HISTORY','View Store Key History','View all key holders and key custody history for the store.','Employee','Store Keys')
 ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
 SELECT r.role_id,p.permission_id,CURRENT_TIMESTAMP FROM public.roles r CROSS JOIN public.permissions p
 WHERE upper(r.role_name)='ADMIN' AND p.permission_key IN
 ('STORE_KEYS_SELF','STORE_KEYS_ASSIST','STORE_KEYS_CONFIGURE','STORE_KEYS_FORCE_RETURN','STORE_KEYS_HISTORY')
 ON CONFLICT(role_id,permission_id) DO NOTHING;
