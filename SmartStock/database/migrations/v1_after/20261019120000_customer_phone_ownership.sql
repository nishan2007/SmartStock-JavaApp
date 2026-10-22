-- Preserve existing accounts and financial references, including legacy duplicates.
-- Reservations survive phone changes, deactivation and deletion: numbers cannot be reused.
CREATE OR REPLACE FUNCTION public.customer_phone_key(value text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
  WITH digits AS (SELECT regexp_replace(COALESCE(value,''),'[^0-9]','','g') AS n),
  international AS (SELECT CASE WHEN left(n,2)='00' THEN substr(n,3) ELSE n END AS n FROM digits)
  SELECT CASE WHEN n='' THEN NULL WHEN length(n)=7 THEN '+592'||n ELSE '+'||n END FROM international
$$;

CREATE TABLE IF NOT EXISTS public.customer_phone_ownership (
  phone_key text PRIMARY KEY,
  customer_id integer NOT NULL
);
INSERT INTO public.customer_phone_ownership(phone_key,customer_id)
SELECT public.customer_phone_key(phone),min(customer_id)
FROM public.customer_accounts WHERE public.customer_phone_key(phone) IS NOT NULL
GROUP BY public.customer_phone_key(phone) ON CONFLICT(phone_key) DO NOTHING;

CREATE INDEX IF NOT EXISTS customer_accounts_phone_key_idx
ON public.customer_accounts(public.customer_phone_key(phone));

CREATE OR REPLACE FUNCTION public.enforce_customer_phone_ownership() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE key text := public.customer_phone_key(NEW.phone); owner_id integer;
BEGIN
  IF key IS NULL THEN RETURN NEW; END IF;
  -- Legacy duplicates may still be edited without moving their phone numbers.
  IF TG_OP='UPDATE' THEN
    IF NEW.customer_id=OLD.customer_id AND key IS NOT DISTINCT FROM public.customer_phone_key(OLD.phone) THEN RETURN NEW; END IF;
  END IF;
  INSERT INTO public.customer_phone_ownership(phone_key,customer_id)
  VALUES(key,NEW.customer_id) ON CONFLICT(phone_key) DO NOTHING;
  SELECT customer_id INTO owner_id FROM public.customer_phone_ownership WHERE phone_key=key;
  IF owner_id IS DISTINCT FROM NEW.customer_id THEN
    RAISE EXCEPTION 'This phone number already belongs to a customer account. Select the existing account or use a different phone number.'
      USING ERRCODE='23505', CONSTRAINT='customer_phone_ownership_pkey';
  END IF;
  RETURN NEW;
END
$$;
DROP TRIGGER IF EXISTS customer_phone_ownership_guard ON public.customer_accounts;
CREATE TRIGGER customer_phone_ownership_guard BEFORE INSERT OR UPDATE OF phone,customer_id
ON public.customer_accounts FOR EACH ROW EXECUTE FUNCTION public.enforce_customer_phone_ownership();
