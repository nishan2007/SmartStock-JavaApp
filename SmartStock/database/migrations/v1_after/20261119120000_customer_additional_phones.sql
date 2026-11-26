ALTER TABLE public.customer_accounts ADD COLUMN IF NOT EXISTS additional_phone_1 text;
ALTER TABLE public.customer_accounts ADD COLUMN IF NOT EXISTS additional_phone_2 text;
CREATE INDEX IF NOT EXISTS customer_accounts_additional_phone_1_key_idx ON public.customer_accounts(public.customer_phone_key(additional_phone_1));
CREATE INDEX IF NOT EXISTS customer_accounts_additional_phone_2_key_idx ON public.customer_accounts(public.customer_phone_key(additional_phone_2));
CREATE OR REPLACE FUNCTION public.enforce_customer_phone_ownership() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE key text; owner_id integer;
BEGIN
 IF public.canonical_customer_id(NEW.customer_id)<>NEW.customer_id THEN RETURN NEW; END IF;
 IF (public.customer_phone_key(NEW.phone) IS NOT NULL AND public.customer_phone_key(NEW.phone) IN (public.customer_phone_key(NEW.additional_phone_1),public.customer_phone_key(NEW.additional_phone_2))) OR (public.customer_phone_key(NEW.additional_phone_1) IS NOT NULL AND public.customer_phone_key(NEW.additional_phone_1)=public.customer_phone_key(NEW.additional_phone_2)) THEN
 RAISE EXCEPTION 'Enter a different number in each phone field.' USING ERRCODE='23514'; END IF;
 FOR key IN SELECT DISTINCT public.customer_phone_key(n) FROM unnest(ARRAY[NEW.phone,NEW.additional_phone_1,NEW.additional_phone_2]) n WHERE public.customer_phone_key(n) IS NOT NULL LOOP
 IF TG_OP='UPDATE' THEN
 IF NEW.customer_id=OLD.customer_id AND key=ANY(ARRAY[public.customer_phone_key(OLD.phone),public.customer_phone_key(OLD.additional_phone_1),public.customer_phone_key(OLD.additional_phone_2)]) THEN CONTINUE; END IF;
 END IF;
 INSERT INTO public.customer_phone_ownership AS ownership(phone_key,customer_id) VALUES(key,NEW.customer_id)
 ON CONFLICT(phone_key) DO UPDATE SET customer_id=EXCLUDED.customer_id
 WHERE NOT EXISTS(SELECT 1 FROM public.customer_accounts ca WHERE key=ANY(ARRAY[public.customer_phone_key(ca.phone),public.customer_phone_key(ca.additional_phone_1),public.customer_phone_key(ca.additional_phone_2)]) AND ca.customer_id<>NEW.customer_id AND ca.customer_id=public.canonical_customer_id(ca.customer_id));
 SELECT customer_id INTO owner_id FROM public.customer_phone_ownership WHERE phone_key=key;
 IF owner_id IS DISTINCT FROM NEW.customer_id THEN RAISE EXCEPTION 'This phone number already belongs to a customer account. Select the existing account or use a different phone number.' USING ERRCODE='23505',CONSTRAINT='customer_phone_ownership_pkey'; END IF;
 END LOOP; RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION public.release_removed_customer_phone() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 DELETE FROM public.customer_phone_ownership ownership WHERE ownership.phone_key=ANY(ARRAY[public.customer_phone_key(OLD.phone),public.customer_phone_key(OLD.additional_phone_1),public.customer_phone_key(OLD.additional_phone_2)]) AND NOT EXISTS(SELECT 1 FROM public.customer_accounts ca WHERE ownership.phone_key=ANY(ARRAY[public.customer_phone_key(ca.phone),public.customer_phone_key(ca.additional_phone_1),public.customer_phone_key(ca.additional_phone_2)]) AND ca.customer_id=public.canonical_customer_id(ca.customer_id)); RETURN NULL;
END $$;
DROP TRIGGER IF EXISTS customer_phone_ownership_guard ON public.customer_accounts;
CREATE TRIGGER customer_phone_ownership_guard BEFORE INSERT OR UPDATE OF phone,additional_phone_1,additional_phone_2,customer_id ON public.customer_accounts FOR EACH ROW EXECUTE FUNCTION public.enforce_customer_phone_ownership();
DROP TRIGGER IF EXISTS customer_phone_release ON public.customer_accounts;
CREATE TRIGGER customer_phone_release AFTER UPDATE OF phone,additional_phone_1,additional_phone_2,customer_id OR DELETE ON public.customer_accounts FOR EACH ROW EXECUTE FUNCTION public.release_removed_customer_phone();
ALTER TABLE public.customer_accounts ADD COLUMN IF NOT EXISTS phone_label varchar(50);
ALTER TABLE public.customer_accounts ADD COLUMN IF NOT EXISTS additional_phone_1_label varchar(50);
ALTER TABLE public.customer_accounts ADD COLUMN IF NOT EXISTS additional_phone_2_label varchar(50);
