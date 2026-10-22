-- POS tables remain local; cloud recovery uses smartstock_store_rows.
DO $smartstock_local$ BEGIN
IF to_regclass('public.custom_orders') IS NOT NULL THEN
-- Local store authority; mirrored off-site only for synchronization/recovery.
ALTER TABLE public.custom_orders ADD COLUMN IF NOT EXISTS order_type text NOT NULL DEFAULT 'CUSTOM';
ALTER TABLE public.custom_order_payments ADD COLUMN IF NOT EXISTS overseas_receipt jsonb;
CREATE TABLE IF NOT EXISTS public.overseas_orders (
    overseas_order_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id integer NOT NULL REFERENCES public.locations(location_id),
    customer_id integer REFERENCES public.customer_accounts(customer_id),
    customer_name text NOT NULL,
    customer_phone text NOT NULL,
    expected_date date,
    notes text NOT NULL DEFAULT '',
    shipping numeric(12,2) NOT NULL DEFAULT 0 CHECK (shipping >= 0),
    total numeric(12,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    state text NOT NULL DEFAULT 'DRAFT' CHECK (state IN ('DRAFT','ISSUED','COMPLETED','CANCELLED')),
    custom_order_id bigint UNIQUE REFERENCES public.custom_orders(custom_order_id),
    revision bigint NOT NULL DEFAULT 1,
    created_by integer NOT NULL REFERENCES public.users(user_id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS public.overseas_order_lines (
    line_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    overseas_order_id uuid NOT NULL REFERENCES public.overseas_orders(overseas_order_id),
    description text NOT NULL,
    product_url text NOT NULL DEFAULT '',
    options text NOT NULL DEFAULT '',
    quantity integer NOT NULL CHECK (quantity > 0 AND quantity <= 100000),
    unit_price numeric(12,2) NOT NULL CHECK (unit_price >= 0),
    usd_cost numeric(12,2) CHECK (usd_cost >= 0),
    exchange_rate numeric(12,4) CHECK (exchange_rate > 0),
    screenshot text NOT NULL DEFAULT '',
    purchased integer NOT NULL DEFAULT 0,
    shipped integer NOT NULL DEFAULT 0,
    arrived integer NOT NULL DEFAULT 0,
    collected integer NOT NULL DEFAULT 0,
    sort_order integer NOT NULL DEFAULT 0,
    CHECK (0 <= collected AND collected <= arrived AND arrived <= shipped AND shipped <= purchased AND purchased <= quantity)
);
CREATE TABLE IF NOT EXISTS public.overseas_order_events (
    event_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    overseas_order_id uuid NOT NULL REFERENCES public.overseas_orders(overseas_order_id),
    actor_id integer NOT NULL,
    actor_name text NOT NULL,
    action text NOT NULL,
    reason text NOT NULL DEFAULT '',
    before_value jsonb,
    after_value jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS public.overseas_order_images (
    image_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    overseas_order_id uuid NOT NULL REFERENCES public.overseas_orders(overseas_order_id),
    asset_reference text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS overseas_order_images_order_idx ON public.overseas_order_images(overseas_order_id);
CREATE TABLE IF NOT EXISTS public.overseas_order_requests (
    request_id uuid NOT NULL,
    actor_id integer NOT NULL,
    location_id integer NOT NULL,
    request_hash text NOT NULL,
    response jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (actor_id,location_id,request_id)
);
CREATE INDEX IF NOT EXISTS overseas_orders_lookup_idx ON public.overseas_orders(location_id,state,updated_at DESC);
CREATE INDEX IF NOT EXISTS overseas_order_lines_order_idx ON public.overseas_order_lines(overseas_order_id,sort_order);
CREATE INDEX IF NOT EXISTS overseas_order_events_order_idx ON public.overseas_order_events(overseas_order_id,created_at);
CREATE INDEX IF NOT EXISTS custom_orders_type_location_idx ON public.custom_orders(order_type,location_id,created_at DESC);

END IF;
END $smartstock_local$;
INSERT INTO public.mobile_permissions(permission_key,permission_name,description,permission_group,permission_subgroup,created_at)
VALUES
 ('OVERSEAS_ORDERS_VIEW','View Overseas Orders','View quotes, receipts and shipment progress.','Orders','Overseas Orders',now()),
 ('OVERSEAS_ORDERS_QUOTE','Prepare Overseas Orders','Create and edit quotes, including private cost references.','Orders','Overseas Orders',now()),
 ('OVERSEAS_ORDERS_PAYMENT','Collect Overseas Payments','Collect payments at an authorized register.','Orders','Overseas Orders',now()),
 ('OVERSEAS_ORDERS_SHIP','Update Overseas Shipments','Record purchased, shipped and arrived quantities.','Orders','Overseas Orders',now()),
 ('OVERSEAS_ORDERS_COLLECT','Hand Over Overseas Orders','Record partial and final customer collections.','Orders','Overseas Orders',now()),
 ('OVERSEAS_ORDERS_ADJUST','Adjust Overseas Orders','Authorize financial corrections, cancellation and refunds.','Orders','Overseas Orders',now())
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_mobile_permissions(role_id,permission_key,updated_at)
SELECT r.role_id,p.permission_key,now() FROM public.roles r CROSS JOIN public.mobile_permissions p
WHERE upper(r.role_name)='ADMIN' AND p.permission_key LIKE 'OVERSEAS_ORDERS_%'
ON CONFLICT(role_id,permission_key) DO NOTHING;
-- POS tables remain local; cloud recovery uses smartstock_store_rows.
DO $smartstock_local$ BEGIN
IF to_regclass('public.custom_orders') IS NOT NULL THEN
-- Off-site mirrors are accessible only to the server service role.
DO $$ DECLARE t text; BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN
  FOREACH t IN ARRAY ARRAY['overseas_orders','overseas_order_lines','overseas_order_images','overseas_order_events','overseas_order_requests'] LOOP
   EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);
   EXECUTE format('REVOKE ALL ON public.%I FROM PUBLIC',t);
   IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN EXECUTE format('REVOKE ALL ON public.%I FROM anon',t); END IF;
   IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN EXECUTE format('REVOKE ALL ON public.%I FROM authenticated',t); END IF;
   EXECUTE format('GRANT ALL ON public.%I TO service_role',t);
  END LOOP;
 END IF;
END $$;

END IF;
END $smartstock_local$;
