-- Numeric keys remain store-local. These identities travel with catalog snapshots.
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS catalog_uid uuid;
ALTER TABLE public.item_brands ADD COLUMN IF NOT EXISTS catalog_uid uuid;
ALTER TABLE public.vendors ADD COLUMN IF NOT EXISTS catalog_uid uuid;
ALTER TABLE public.item_types ADD COLUMN IF NOT EXISTS catalog_uid uuid;
ALTER TABLE public.products ADD COLUMN IF NOT EXISTS catalog_uid uuid;

CREATE OR REPLACE FUNCTION public.catalog_legacy_uid(entity_table text, row_data jsonb)
RETURNS uuid LANGUAGE plpgsql STABLE AS $$
DECLARE identity_text text; parent_uid uuid;
BEGIN
    IF entity_table='products' THEN
        identity_text := 'products:'||(row_data->>'product_id')||':'||
            to_char((row_data->>'created_at')::timestamptz AT TIME ZONE 'UTC',
                    'YYYY-MM-DD"T"HH24:MI:SS.US"Z"');
    ELSIF entity_table='item_types' THEN
        SELECT catalog_uid INTO STRICT parent_uid FROM public.categories
            WHERE category_id=(row_data->>'category_id')::integer;
        identity_text := entity_table||':'||upper(regexp_replace(btrim(row_data->>'name'),'\s+',' ','g'))||':'||parent_uid::text;
    ELSIF entity_table='categories' THEN
        identity_text := entity_table||':'||lower(row_data->>'name');
    ELSIF entity_table='vendors' THEN
        identity_text := entity_table||':'||(row_data->>'name');
    ELSIF entity_table='item_brands' THEN
        identity_text := entity_table||':'||upper(regexp_replace(btrim(row_data->>'name'),'\s+',' ','g'));
    ELSE RAISE EXCEPTION 'Unsupported catalog entity';
    END IF;
    IF identity_text IS NULL THEN RAISE EXCEPTION 'Incomplete legacy catalog identity'; END IF;
    RETURN md5('SmartStock/catalog/v1/'||identity_text)::uuid;
END $$;

UPDATE public.categories SET catalog_uid=public.catalog_legacy_uid('categories',to_jsonb(categories)) WHERE catalog_uid IS NULL;
UPDATE public.item_brands SET catalog_uid=public.catalog_legacy_uid('item_brands',to_jsonb(item_brands)) WHERE catalog_uid IS NULL;
UPDATE public.vendors SET catalog_uid=public.catalog_legacy_uid('vendors',to_jsonb(vendors)) WHERE catalog_uid IS NULL;
UPDATE public.products SET catalog_uid=public.catalog_legacy_uid('products',to_jsonb(products)) WHERE catalog_uid IS NULL;
UPDATE public.item_types SET catalog_uid=public.catalog_legacy_uid('item_types',to_jsonb(item_types)) WHERE catalog_uid IS NULL;

CREATE OR REPLACE FUNCTION public.preserve_catalog_uid() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='UPDATE' AND OLD.catalog_uid IS NOT NULL THEN
        IF NEW.catalog_uid IS NOT NULL AND NEW.catalog_uid<>OLD.catalog_uid THEN
            RAISE EXCEPTION 'Catalog identity cannot be changed';
        END IF;
        NEW.catalog_uid:=OLD.catalog_uid;
    ELSIF NEW.catalog_uid IS NULL THEN
        -- Older recovery snapshots supply explicit NULL rather than using column defaults.
        NEW.catalog_uid:=public.catalog_legacy_uid(TG_TABLE_NAME,to_jsonb(NEW));
    END IF;
    RETURN NEW;
END $$;

DO $$
DECLARE entity text;
BEGIN
    FOREACH entity IN ARRAY ARRAY['categories','item_brands','vendors','item_types','products'] LOOP
        EXECUTE format('ALTER TABLE public.%I ALTER COLUMN catalog_uid SET DEFAULT gen_random_uuid()',entity);
        EXECUTE format('ALTER TABLE public.%I ALTER COLUMN catalog_uid SET NOT NULL',entity);
        EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS %I ON public.%I(catalog_uid)',entity||'_catalog_uid_idx',entity);
        EXECUTE format('DROP TRIGGER IF EXISTS preserve_catalog_uid ON public.%I',entity);
        EXECUTE format('CREATE TRIGGER preserve_catalog_uid BEFORE INSERT OR UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.preserve_catalog_uid()',entity);
    END LOOP;
END $$;

CREATE TABLE IF NOT EXISTS public.catalog_source_ids (
    entity_table text NOT NULL CHECK(entity_table IN ('categories','item_brands','vendors','item_types','products')),
    source_location_id integer NOT NULL CHECK(source_location_id>0),
    source_id integer NOT NULL CHECK(source_id>0),
    source_uid uuid NOT NULL,
    local_uid uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(entity_table,source_location_id,source_id,source_uid)
);
CREATE INDEX IF NOT EXISTS catalog_source_ids_uid_idx ON public.catalog_source_ids(entity_table,source_uid);


