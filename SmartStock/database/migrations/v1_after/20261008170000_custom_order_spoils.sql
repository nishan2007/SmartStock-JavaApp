-- Internal evidence only; never shared with custom order customer links.
CREATE TABLE IF NOT EXISTS public.custom_order_spoils (
 spoil_id uuid PRIMARY KEY,
 location_id integer NOT NULL,
 custom_order_id bigint NOT NULL REFERENCES public.custom_orders(custom_order_id),
 custom_order_line_id bigint NOT NULL REFERENCES public.custom_order_lines(custom_order_line_id),
 custom_item_id bigint,
 custom_variant_id bigint,
 stock_deducted boolean NOT NULL,
 stock_after numeric(12,2),
 reason text NOT NULL CHECK(length(trim(reason)) BETWEEN 1 AND 2000),
 created_by_user_id integer NOT NULL,
 created_by_name text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 reversed_at timestamptz,
 reversed_by_user_id integer,
 reversed_by_name text,
 reversal_reason text,
 updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK ((reversed_at IS NULL AND reversal_reason IS NULL) OR
        (reversed_at IS NOT NULL AND length(trim(reversal_reason)) BETWEEN 1 AND 2000))
);
CREATE TABLE IF NOT EXISTS public.custom_order_spoil_photos (
 photo_id uuid PRIMARY KEY,
 location_id integer NOT NULL,
 spoil_id uuid NOT NULL REFERENCES public.custom_order_spoils(spoil_id),
 content_type text NOT NULL DEFAULT 'image/jpeg' CHECK(content_type='image/jpeg'),
 bytes_base64 text NOT NULL CHECK(length(bytes_base64) BETWEEN 1 AND 2796204),
 sha256 text NOT NULL CHECK(length(sha256)=64),
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS custom_order_spoils_order_idx ON public.custom_order_spoils(location_id,custom_order_id,created_at);
CREATE INDEX IF NOT EXISTS custom_order_spoils_line_idx ON public.custom_order_spoils(custom_order_line_id,created_at);
CREATE INDEX IF NOT EXISTS custom_order_spoil_photos_report_idx ON public.custom_order_spoil_photos(spoil_id);
ALTER TABLE public.custom_order_spoils ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.custom_order_spoil_photos ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.custom_order_spoils,public.custom_order_spoil_photos FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
  REVOKE ALL ON public.custom_order_spoils,public.custom_order_spoil_photos FROM anon;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
  REVOKE ALL ON public.custom_order_spoils,public.custom_order_spoil_photos FROM authenticated;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN
  GRANT ALL ON public.custom_order_spoils,public.custom_order_spoil_photos TO service_role;
 END IF;
END $$;
INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('RECORD_CUSTOM_ORDER_SPOILS','Record Custom Order Spoils','Record internal spoil evidence and consume replacement inventory.','Custom Orders','Orders'),
 ('REVERSE_CUSTOM_ORDER_SPOILS','Reverse Custom Order Spoils','Reverse a spoil with a reason and restore replacement stock.','Custom Orders','Orders')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,now() FROM public.roles r CROSS JOIN public.permissions p
WHERE UPPER(r.role_name)='ADMIN' AND p.permission_key IN ('RECORD_CUSTOM_ORDER_SPOILS','REVERSE_CUSTOM_ORDER_SPOILS')
ON CONFLICT(role_id,permission_id) DO NOTHING;
