-- Local authority and recovery metadata. Never delete retired identities.
CREATE TABLE IF NOT EXISTS public.customer_account_merges (
  source_customer_id integer PRIMARY KEY,
  target_customer_id integer NOT NULL,
  source_uuid uuid NOT NULL UNIQUE,
  target_uuid uuid NOT NULL,
  merged_by_user_id integer NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (source_customer_id > target_customer_id),
  CHECK (source_uuid <> target_uuid)
);
CREATE INDEX IF NOT EXISTS customer_account_merges_target_idx ON public.customer_account_merges(target_customer_id);

CREATE OR REPLACE FUNCTION public.canonical_customer_id(value integer) RETURNS integer
LANGUAGE sql STABLE AS $$
  WITH RECURSIVE chain(id) AS (
    SELECT value UNION ALL
    SELECT m.target_customer_id FROM public.customer_account_merges m JOIN chain c ON m.source_customer_id=c.id
  ) SELECT min(id) FROM chain
$$;
CREATE OR REPLACE FUNCTION public.canonical_customer_uuid(value uuid) RETURNS uuid
LANGUAGE sql STABLE AS $$
  SELECT COALESCE((SELECT ca.sync_uuid FROM public.customer_accounts ca
    WHERE ca.customer_id=public.canonical_customer_id((SELECT customer_id FROM public.customer_accounts WHERE sync_uuid=value))),value)
$$;

-- Include declared FKs as well as older customer_id columns lacking FKs (sales,
-- sales_orders, history caches), and the storefront's UUID references.
CREATE OR REPLACE FUNCTION public.customer_reference_columns()
RETURNS TABLE(schema_name text,table_name text,column_name text,reference_kind text)
LANGUAGE sql STABLE AS $$
  SELECT DISTINCT n.nspname::text,t.relname::text,a.attname::text,
    CASE WHEN a.atttypid='uuid'::regtype THEN 'UUID' ELSE 'ID' END
  FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
  JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped
  WHERE t.relkind IN ('r','p') AND n.nspname IN ('public','storefront')
    AND t.relname NOT IN ('customer_accounts','customer_account_merges')
    AND ((a.attname='customer_id' AND a.atttypid IN ('integer'::regtype,'bigint'::regtype))
      OR (n.nspname='storefront' AND a.attname='customer_uuid' AND a.atttypid='uuid'::regtype)
      OR EXISTS(SELECT 1 FROM pg_constraint fk WHERE fk.contype='f' AND fk.conrelid=t.oid
        AND fk.confrelid='public.customer_accounts'::regclass
        AND array_length(fk.conkey,1)=1 AND fk.conkey[1]=a.attnum))
$$;

CREATE OR REPLACE FUNCTION public.redirect_merged_customer_reference() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE old_value jsonb := to_jsonb(NEW)->TG_ARGV[0]; replacement jsonb;
BEGIN
  IF old_value IS NULL OR old_value='null'::jsonb THEN RETURN NEW; END IF;
  IF TG_ARGV[1]='UUID' THEN
    replacement := to_jsonb(public.canonical_customer_uuid((old_value#>>'{}')::uuid));
  ELSE
    replacement := to_jsonb(public.canonical_customer_id((old_value#>>'{}')::integer));
  END IF;
  IF replacement IS DISTINCT FROM old_value THEN
    NEW := jsonb_populate_record(NEW,jsonb_build_object(TG_ARGV[0],replacement));
  END IF;
  RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION public.install_customer_merge_reference_guards() RETURNS void
LANGUAGE plpgsql AS $$
DECLARE ref record;
BEGIN
  FOR ref IN SELECT * FROM public.customer_reference_columns() WHERE schema_name='public' ORDER BY schema_name,table_name,column_name LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS %I ON %I.%I','customer_merge_'||ref.column_name,ref.schema_name,ref.table_name);
    EXECUTE format('CREATE TRIGGER %I BEFORE INSERT OR UPDATE ON %I.%I FOR EACH ROW EXECUTE FUNCTION public.redirect_merged_customer_reference(%L,%L)',
      'customer_merge_'||ref.column_name,ref.schema_name,ref.table_name,ref.column_name,ref.reference_kind);
  END LOOP;
END
$$;
SELECT public.install_customer_merge_reference_guards();

CREATE OR REPLACE FUNCTION public.keep_merged_customer_retired() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS(SELECT 1 FROM public.customer_account_merges WHERE source_customer_id=NEW.customer_id) THEN
    NEW.is_active := FALSE;
    NEW.current_balance := 0;
    NEW.whatsapp_opt_in := FALSE;
    -- The canonical account and audit retain this shared phone. Keeping it on
    -- retired rows would collide with ownership when recovering accounts before mappings.
    NEW.phone := NULL;
  END IF;
  RETURN NEW;
END
$$;
DROP TRIGGER IF EXISTS customer_merge_retired ON public.customer_accounts;
CREATE TRIGGER customer_merge_retired BEFORE INSERT OR UPDATE ON public.customer_accounts
FOR EACH ROW EXECUTE FUNCTION public.keep_merged_customer_retired();

-- Extend ownership arbitration to retained aliases. Snapshots may contain the
-- original retired account; only its canonical account owns the number.
CREATE OR REPLACE FUNCTION public.enforce_customer_phone_ownership() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE key text := public.customer_phone_key(NEW.phone); owner_id integer;
BEGIN
  IF key IS NULL OR public.canonical_customer_id(NEW.customer_id)<>NEW.customer_id THEN RETURN NEW; END IF;
  IF TG_OP='UPDATE' THEN
    IF NEW.customer_id=OLD.customer_id AND key IS NOT DISTINCT FROM public.customer_phone_key(OLD.phone) THEN RETURN NEW; END IF;
  END IF;
  INSERT INTO public.customer_phone_ownership(phone_key,customer_id)
  VALUES(key,NEW.customer_id) ON CONFLICT(phone_key) DO NOTHING;
  SELECT public.canonical_customer_id(customer_id) INTO owner_id FROM public.customer_phone_ownership WHERE phone_key=key;
  IF owner_id IS DISTINCT FROM NEW.customer_id THEN
    RAISE EXCEPTION 'This phone number already belongs to a customer account. Select the existing account or use a different phone number.'
      USING ERRCODE='23505', CONSTRAINT='customer_phone_ownership_pkey';
  END IF;
  RETURN NEW;
END
$$;

-- Immutable mappings are safe to replay. An alias always points to a lower ID,
-- so repeated merges cannot create a cycle.
CREATE OR REPLACE FUNCTION public.apply_customer_account_merge() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE ref record; destination integer; destination_uuid uuid; source_balance numeric;
BEGIN
  IF TG_OP<>'INSERT' THEN
    IF NEW IS DISTINCT FROM OLD THEN RAISE EXCEPTION 'Customer merge mappings cannot be changed.'; END IF;
    RETURN NEW;
  END IF;
  destination := public.canonical_customer_id(NEW.target_customer_id);
  PERFORM customer_id FROM public.customer_accounts WHERE customer_id IN (NEW.source_customer_id,destination) ORDER BY customer_id FOR UPDATE;
  IF NOT EXISTS(SELECT 1 FROM public.customer_accounts WHERE customer_id=NEW.source_customer_id AND sync_uuid=NEW.source_uuid)
    OR NOT EXISTS(SELECT 1 FROM public.customer_accounts WHERE customer_id=NEW.target_customer_id AND sync_uuid=NEW.target_uuid) THEN
    RAISE EXCEPTION 'Customer merge identities are not available in this store.';
  END IF;
  SELECT sync_uuid INTO destination_uuid FROM public.customer_accounts WHERE customer_id=destination;
  SELECT COALESCE(current_balance,0) INTO source_balance FROM public.customer_accounts WHERE customer_id=NEW.source_customer_id;
  FOR ref IN SELECT * FROM public.customer_reference_columns() ORDER BY schema_name,table_name,column_name LOOP
    IF ref.reference_kind='UUID' THEN
      EXECUTE format('UPDATE %I.%I SET %I=$1 WHERE %I=$2',ref.schema_name,ref.table_name,ref.column_name,ref.column_name)
        USING destination_uuid,NEW.source_uuid;
    ELSE
      EXECUTE format('UPDATE %I.%I SET %I=$1 WHERE %I=$2',ref.schema_name,ref.table_name,ref.column_name,ref.column_name)
        USING destination,NEW.source_customer_id;
    END IF;
  END LOOP;
  UPDATE public.customer_accounts SET current_balance=COALESCE(current_balance,0)+source_balance,
    is_active=is_active OR (SELECT is_active FROM public.customer_accounts WHERE customer_id=NEW.source_customer_id),
    require_charge_authorization=require_charge_authorization OR
      (SELECT require_charge_authorization FROM public.customer_accounts WHERE customer_id=NEW.source_customer_id),
    updated_at=CURRENT_TIMESTAMP WHERE customer_id=destination;
  UPDATE public.customer_accounts SET is_active=FALSE,current_balance=0,whatsapp_opt_in=FALSE,
    account_notes=concat_ws(E'\n',NULLIF(account_notes,''),'Merged into customer #'||destination),
    updated_at=CURRENT_TIMESTAMP WHERE customer_id=NEW.source_customer_id;
  RETURN NEW;
END
$$;
DROP TRIGGER IF EXISTS apply_customer_account_merge ON public.customer_account_merges;
CREATE TRIGGER apply_customer_account_merge AFTER INSERT OR UPDATE ON public.customer_account_merges
FOR EACH ROW EXECUTE FUNCTION public.apply_customer_account_merge();

CREATE OR REPLACE FUNCTION public.preserve_customer_merge_identity() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_TABLE_NAME='customer_account_merges' THEN
    RAISE EXCEPTION 'Customer merge history cannot be deleted.';
  END IF;
  IF EXISTS(SELECT 1 FROM public.customer_account_merges WHERE source_customer_id=OLD.customer_id OR target_customer_id=OLD.customer_id) THEN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Merged customer identities cannot be deleted.'; END IF;
    IF NEW.customer_id IS DISTINCT FROM OLD.customer_id OR NEW.sync_uuid IS DISTINCT FROM OLD.sync_uuid THEN
      RAISE EXCEPTION 'Merged customer identities cannot be changed.';
    END IF;
  END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END
$$;
DROP TRIGGER IF EXISTS preserve_customer_merge_identity ON public.customer_accounts;
CREATE TRIGGER preserve_customer_merge_identity BEFORE DELETE OR UPDATE OF customer_id,sync_uuid ON public.customer_accounts
FOR EACH ROW EXECUTE FUNCTION public.preserve_customer_merge_identity();
DROP TRIGGER IF EXISTS preserve_customer_merge_history ON public.customer_account_merges;
CREATE TRIGGER preserve_customer_merge_history BEFORE DELETE ON public.customer_account_merges
FOR EACH ROW EXECUTE FUNCTION public.preserve_customer_merge_identity();
