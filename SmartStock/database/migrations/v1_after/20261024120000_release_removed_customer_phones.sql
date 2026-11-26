-- Phone ownership describes current contact details, not historical order snapshots.
LOCK TABLE public.customer_accounts IN SHARE ROW EXCLUSIVE MODE;
DELETE FROM public.customer_phone_ownership;
INSERT INTO public.customer_phone_ownership(phone_key,customer_id)
SELECT public.customer_phone_key(phone),min(customer_id)
FROM public.customer_accounts
WHERE public.customer_phone_key(phone) IS NOT NULL
  AND customer_id=public.canonical_customer_id(customer_id)
GROUP BY public.customer_phone_key(phone);

CREATE OR REPLACE FUNCTION public.enforce_customer_phone_ownership() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE key text := public.customer_phone_key(NEW.phone); owner_id integer;
BEGIN
  IF key IS NULL OR public.canonical_customer_id(NEW.customer_id)<>NEW.customer_id THEN RETURN NEW; END IF;
  -- Preserve existing legacy duplicates when their normalized number is unchanged.
  IF TG_OP='UPDATE' THEN
    IF NEW.customer_id=OLD.customer_id AND key IS NOT DISTINCT FROM public.customer_phone_key(OLD.phone) THEN RETURN NEW; END IF;
  END IF;
  -- The unique key serializes competing claims, including claims of stale mirrored rows.
  INSERT INTO public.customer_phone_ownership AS ownership(phone_key,customer_id)
  VALUES(key,NEW.customer_id) ON CONFLICT(phone_key) DO UPDATE SET customer_id=EXCLUDED.customer_id
  WHERE NOT EXISTS(SELECT 1 FROM public.customer_accounts ca
    WHERE public.customer_phone_key(ca.phone)=key AND ca.customer_id<>NEW.customer_id
      AND ca.customer_id=public.canonical_customer_id(ca.customer_id));
  SELECT customer_id INTO owner_id FROM public.customer_phone_ownership WHERE phone_key=key;
  IF owner_id IS DISTINCT FROM NEW.customer_id OR EXISTS(
    SELECT 1 FROM public.customer_accounts ca
    WHERE public.customer_phone_key(ca.phone)=key AND ca.customer_id<>NEW.customer_id
      AND ca.customer_id=public.canonical_customer_id(ca.customer_id)) THEN
    RAISE EXCEPTION 'This phone number already belongs to a customer account. Select the existing account or use a different phone number.'
      USING ERRCODE='23505', CONSTRAINT='customer_phone_ownership_pkey';
  END IF;
  RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION public.release_removed_customer_phone() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  DELETE FROM public.customer_phone_ownership ownership
  WHERE ownership.phone_key=public.customer_phone_key(OLD.phone)
    AND NOT EXISTS(SELECT 1 FROM public.customer_accounts ca
      WHERE public.customer_phone_key(ca.phone)=ownership.phone_key
        AND ca.customer_id=public.canonical_customer_id(ca.customer_id));
  RETURN NULL;
END
$$;
DROP TRIGGER IF EXISTS customer_phone_release ON public.customer_accounts;
CREATE TRIGGER customer_phone_release AFTER UPDATE OF phone,customer_id OR DELETE
ON public.customer_accounts FOR EACH ROW EXECUTE FUNCTION public.release_removed_customer_phone();
